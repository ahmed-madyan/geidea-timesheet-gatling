package gatling.endpoints.timesheet.tasks;

import gatling.builders.ChainBuilderFactory;
import gatling.config.enums.timesheet.TimesheetBasePath;
import gatling.endpoints.timesheet.TimesheetPage;
import gatling.utils.RetryFactory;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.gatling.javaapi.core.CoreDsl.bodyString;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.core.CoreDsl.listFeeder;
import static io.gatling.javaapi.http.HttpDsl.status;

public final class TaskLogChain {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskLogChain.class);
    private static final String FEEDER = "data/timesheet/task-varieties.csv";
    private static final String RESULTS = "taskResults";
    private static final String DATE_COLUMN = "Date";
    private static final DateTimeFormatter TASK_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter[] NAMED_DATES = {
            named("d MMM uuuu"),
            named("d-MMM-uuuu"),
            named("d MMMM uuuu"),
            named("MMM d uuuu"),
            named("MMM d, uuuu"),
            named("MMMM d uuuu"),
            named("MMMM d, uuuu")
    };
    private static final int TWO_DIGIT_YEAR_PIVOT = 70;

    private TaskLogChain() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ChainBuilder openEntry() {
        String path = TimesheetBasePath.resolve(TimesheetBasePath.TASK_LOG, "timesheet.taskLogPath");
        return TimesheetPage.get(
                "Timesheet | Open entry",
                path,
                "form:not([action]) input[name='__RequestVerificationToken']");
    }

    public static ChainBuilder logOneVariety() {
        String path = TimesheetBasePath.resolve(TimesheetBasePath.TASK_LOG, "timesheet.taskLogPath");
        return new ChainBuilderFactory("Timesheet | Log #{Task Name}")
                .post(path)
                .withHeader("Content-Type", "application/x-www-form-urlencoded")
                .withFormParam("Input.RequestId", "#{ProjectId}")
                .withFormParam("Input.TaskName", "#{Task Name}")
                .withFormParam("Input.Description", "#{Details / Description}")
                .withFormParam("Input.TaskDate", "#{Date}")
                .withFormParam("Input.Hours", "#{Hours}")
                .withFormParam("__RequestVerificationToken", "#{csrf}")
                .disableFollowRedirect()
                .withCheck(status().in(200, 302).saveAs("lastStatus"))
                .withCheck(bodyString().optional().saveAs("taskResponse"))
                .build();
    }

    /** Signs in, then posts every data row in task-varieties.csv. */
    public static ChainBuilder build() {
        List<Map<String, Object>> rows = varietyRows();
        return exec(gatling.endpoints.timesheet.auth.TimesheetLoginChain.build())
                .repeat(rows.size()).on(
                        feed(listFeeder(rows).queue()),
                        RetryFactory.withRetry("openEntry", openEntry()),
                        RetryFactory.withRetry("logTask", logOneVariety()),
                        exec(TaskLogChain::recordTask))
                .exec(TaskLogChain::reportTasks);
    }

    static Session recordTask(Session session) {
        String outcome = outcome(session);
        String line = session.getString("Task Name")
                + " | " + session.getString("Date")
                + " | " + session.getString("Hours") + "h"
                + " | project " + session.getString("ProjectId")
                + " | " + outcome;
        List<String> results = session.contains(RESULTS)
                ? new ArrayList<>(session.getList(RESULTS))
                : new ArrayList<>();
        results.add(line);
        return session.set(RESULTS, results);
    }

    static Session reportTasks(Session session) {
        List<String> results = session.contains(RESULTS) ? session.getList(RESULTS) : List.of();
        int logged = 0;
        int duplicates = 0;
        int missed = 0;
        for (String line : results) {
            if (line.endsWith("| logged")) {
                logged++;
            } else if (line.contains("not logged (duplicate)")) {
                duplicates++;
                missed++;
            } else if (line.contains("not logged")) {
                missed++;
            }
        }
        String email = session.contains("username") ? session.getString("username") : "the user";
        StringBuilder summary = new StringBuilder();
        summary.append("The user ").append(email).append(" logged in successfully. ")
                .append(logged).append(" task(s) have been logged and ")
                .append(duplicates).append(" not logged because they are duplicated.");
        for (String line : results) {
            summary.append('\n').append(line);
        }
        LOGGER.info("{}", summary);
        writeSummary(summary.toString());
        if (missed > 0) {
            throw new IllegalStateException(
                    missed + " task(s) were not logged. See the task log results above. " + summary);
        }
        return session;
    }

    private static void writeSummary(String summary) {
        try {
            Path logs = Path.of("logs");
            Files.createDirectories(logs);
            Files.writeString(logs.resolve("timesheet-summary.txt"), summary);
        } catch (IOException e) {
            LOGGER.warn("Could not write the task summary file: {}", e.getMessage());
        }
    }

    private static String outcome(Session session) {
        int status = session.contains("lastStatus") ? session.getInt("lastStatus") : 0;
        String body = session.contains("taskResponse") ? session.getString("taskResponse") : "";
        String lowered = body.toLowerCase();
        if (lowered.contains("task logged successfully")) {
            return "logged";
        }
        if (lowered.contains("already exists")) {
            return "not logged (duplicate)";
        }
        if (status == 200 || status == 302) {
            return "not logged (rejected)";
        }
        return "not logged (HTTP " + status + ")";
    }

    /**
     * Reads task rows and rewrites Date into yyyy-MM-dd.
     * Numeric dates follow whichever order the file itself makes unambiguous.
     */
    static List<Map<String, Object>> varietyRows() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = TaskLogChain.class.getClassLoader();
        }
        try (InputStream in = loader.getResourceAsStream(FEEDER)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + FEEDER);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String headerLine = reader.readLine();
                if (headerLine == null || headerLine.isBlank()) {
                    throw new IllegalStateException(FEEDER + " has no header row");
                }
                if (headerLine.charAt(0) == '\uFEFF') {
                    headerLine = headerLine.substring(1);
                }
                String[] headers = splitCsvLine(headerLine);
                int dateIndex = -1;
                for (int i = 0; i < headers.length; i++) {
                    if (DATE_COLUMN.equals(headers[i].trim())) {
                        dateIndex = i;
                        break;
                    }
                }
                if (dateIndex < 0) {
                    throw new IllegalStateException(FEEDER + " has no Date column");
                }
                List<Map<String, Object>> rows = new ArrayList<>();
                List<Integer> lineNumbers = new ArrayList<>();
                List<String> rawDates = new ArrayList<>();
                String line;
                int lineNumber = 1;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;
                    if (line.isBlank()) {
                        continue;
                    }
                    String[] cells = splitCsvLine(line);
                    Map<String, Object> row = new HashMap<>();
                    for (int i = 0; i < headers.length; i++) {
                        String key = headers[i].trim();
                        String value = i < cells.length ? cells[i].trim() : "";
                        row.put(key, value);
                    }
                    String rawDate = dateIndex < cells.length ? cells[dateIndex].trim() : "";
                    rows.add(row);
                    lineNumbers.add(lineNumber);
                    rawDates.add(rawDate);
                }
                boolean monthFirst = monthFirstOrder(rawDates, lineNumbers);
                for (int i = 0; i < rows.size(); i++) {
                    rows.get(i).put(
                            DATE_COLUMN,
                            normalizeTaskDate(rawDates.get(i), lineNumbers.get(i), monthFirst));
                }
                if (rows.isEmpty()) {
                    throw new IllegalStateException(FEEDER + " has no task rows");
                }
                return rows;
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + FEEDER, e);
        }
    }

    static String normalizeTaskDate(String raw, int lineNumber) {
        return normalizeTaskDate(raw, lineNumber, false);
    }

    static String normalizeTaskDate(String raw, int lineNumber, boolean monthFirst) {
        String value = dateText(raw);
        if (value.isEmpty()) {
            throw new IllegalStateException("Row " + lineNumber + " has an empty Date");
        }
        LocalDate named = parseNamed(value);
        if (named != null) {
            return named.format(TASK_DATE);
        }
        int[] parts = numericParts(value);
        if (parts != null) {
            return numericDate(parts, monthFirst, lineNumber, value).format(TASK_DATE);
        }
        throw new IllegalStateException(
                "Row " + lineNumber + " date \"" + value + "\" is not a day. "
                        + "Use 2026-10-01, 01/10/2026, 9/23/2026, or 23 Sep 2026.");
    }

    /**
     * True when a numeric date in the file can only be month-first, such as 9/23/2026.
     * Mixed orders in one file are rejected. A file with only ambiguous dates stays day-first.
     */
    private static boolean monthFirstOrder(List<String> rawDates, List<Integer> lineNumbers) {
        Boolean monthFirst = null;
        for (int i = 0; i < rawDates.size(); i++) {
            String value = dateText(rawDates.get(i));
            int[] parts = numericParts(value);
            if (parts == null || yearFirst(value)) {
                continue;
            }
            int first = parts[1];
            int second = parts[2];
            Boolean rowOrder = null;
            if (first > 12 && second <= 12) {
                rowOrder = Boolean.FALSE;
            } else if (second > 12 && first <= 12) {
                rowOrder = Boolean.TRUE;
            }
            if (rowOrder == null) {
                continue;
            }
            if (monthFirst != null && !monthFirst.equals(rowOrder)) {
                throw new IllegalStateException(
                        "Row " + lineNumbers.get(i) + " date \"" + value
                                + "\" uses a different day and month order from an earlier row.");
            }
            monthFirst = rowOrder;
        }
        return Boolean.TRUE.equals(monthFirst);
    }

    private static String dateText(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            value = value.substring(1, value.length() - 1).trim();
        }
        return value.replaceFirst("(?i)[T ]\\d{1,2}:\\d{2}(:\\d{2})?(\\s*[AP]M)?$", "").trim();
    }

    private static LocalDate parseNamed(String value) {
        String normalized = value.replace(',', ' ').replaceAll("[/\\-.]", " ").replaceAll("\\s+", " ").trim();
        for (DateTimeFormatter format : NAMED_DATES) {
            try {
                return LocalDate.parse(normalized, format);
            } catch (DateTimeParseException ignored) {
                // try the next month-name pattern
            }
        }
        return null;
    }

    /** Year, month, day parts when the text is three numbers. Year may be first or last. */
    private static int[] numericParts(String value) {
        String[] tokens = value.split("[/\\-.]+");
        if (tokens.length != 3) {
            return null;
        }
        int[] numbers = new int[3];
        for (int i = 0; i < tokens.length; i++) {
            if (!tokens[i].matches("\\d{1,4}")) {
                return null;
            }
            numbers[i] = Integer.parseInt(tokens[i]);
        }
        if (tokens[0].length() == 4) {
            return new int[] {numbers[0], numbers[1], numbers[2]};
        }
        int year = numbers[2];
        if (tokens[2].length() <= 2) {
            year = year + (year >= TWO_DIGIT_YEAR_PIVOT ? 1900 : 2000);
        }
        return new int[] {year, numbers[0], numbers[1]};
    }

    private static LocalDate numericDate(int[] yearThenParts, boolean monthFirst, int lineNumber, String value) {
        int year = yearThenParts[0];
        int first = yearThenParts[1];
        int second = yearThenParts[2];
        int month;
        int day;
        if (first > 12 && second > 12) {
            throw badDate(lineNumber, value);
        }
        if (yearFirst(value)) {
            month = first;
            day = second;
        } else if (first > 12) {
            day = first;
            month = second;
        } else if (second > 12) {
            month = first;
            day = second;
        } else if (monthFirst) {
            month = first;
            day = second;
        } else {
            day = first;
            month = second;
        }
        try {
            return LocalDate.of(year, month, day);
        } catch (RuntimeException ex) {
            throw badDate(lineNumber, value);
        }
    }

    private static boolean yearFirst(String value) {
        return value.matches("\\d{4}[/\\-.]\\d{1,2}[/\\-.]\\d{1,2}");
    }

    private static IllegalStateException badDate(int lineNumber, String value) {
        return new IllegalStateException(
                "Row " + lineNumber + " date \"" + value + "\" is not a day. "
                        + "Use 2026-10-01, 01/10/2026, 9/23/2026, or 23 Sep 2026.");
    }

    private static DateTimeFormatter named(String pattern) {
        return DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT);
    }

    private static String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                    continue;
                }
                inQuotes = !inQuotes;
                continue;
            }
            if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        fields.add(current.toString());
        return fields.toArray(String[]::new);
    }
}
