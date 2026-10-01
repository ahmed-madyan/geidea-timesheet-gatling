package gatling.utils;

import io.gatling.javaapi.core.FeederBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.gatling.javaapi.core.CoreDsl.csv;
import static io.gatling.javaapi.core.CoreDsl.listFeeder;

/**
 * Centralized factory for creating Gatling feeders (CSV, JSON, etc.)
 * with consistent strategies and basic validation.
 *
 * <p>All paths are expected to be classpath-relative, e.g. {@code data/myFile.csv}
 * when the file lives under {@code src/test/resources/data}.
 */
public final class FeederFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(FeederFactory.class);

    /**
     * System property for the CSV header used by {@link FileStrategy#BY_CODE}
     * when the header is not passed explicitly ({@code -DfeederCodeHeader=businessTypeCode}).
     */
    public static final String PROP_CODE_HEADER = "feederCodeHeader";

    /**
     * System property for the row value used by {@link FileStrategy#BY_CODE}
     * when the value is not passed explicitly ({@code -DfeederCode=SOLE_TRADER}).
     */
    public static final String PROP_CODE_VALUE = "feederCode";

    /**
     * Supported strategies for file-based feeders.
     */
    public enum FileStrategy {
        /** Pick a random row each time. */
        RANDOM,
        /** Cycle through rows endlessly. */
        CIRCULAR,
        /** Consume rows once in order (exhausts when empty). */
        QUEUE,
        /**
         * Feed only the row(s) whose value in a given header column matches a code.
         * Requires {@link #csvFile(String, FileStrategy, String, String)} or
         * {@code -DfeederCodeHeader=...} / {@code -DfeederCode=...}.
         */
        BY_CODE
    }

    private FeederFactory() {
        throw new UnsupportedOperationException("FeederFactory is a utility class and cannot be instantiated.");
    }

    /**
     * Creates a CSV file-based feeder with default {@link FileStrategy#CIRCULAR} strategy.
     *
     * @param resourcePath classpath-relative path to the CSV file (e.g. {@code data/gameCsvFile.csv})
     * @return configured CSV feeder (values as {@link String})
     * @throws IllegalArgumentException if the path is null or blank
     */
    public static FeederBuilder<String> csvFile(String resourcePath) {
        return csvFile(resourcePath, FileStrategy.CIRCULAR);
    }

    /**
     * Creates a CSV file-based feeder with the given strategy.
     *
     * <p>Do not pass {@link FileStrategy#BY_CODE} here — use
     * {@link #csvFileByCode(String, String, String)} or
     * {@link #csvFile(String, FileStrategy, String, String)}.
     * System-property form: {@code csvFileByCode(path)} with
     * {@code -DfeederCodeHeader} / {@code -DfeederCode}.
     *
     * @param resourcePath classpath-relative path to the CSV file (e.g. {@code data/gameCsvFile.csv})
     * @param strategy     feeding strategy (RANDOM, CIRCULAR, QUEUE)
     * @return configured CSV feeder (values as {@link String})
     * @throws IllegalArgumentException if the path is null or blank, or strategy is BY_CODE
     */
    public static FeederBuilder<String> csvFile(String resourcePath, FileStrategy strategy) {
        if (strategy == FileStrategy.BY_CODE) {
            throw new IllegalArgumentException(
                    "FileStrategy.BY_CODE requires a header and code. Use "
                            + "csvFileByCode(path, headerName, codeValue) or "
                            + "csvFile(path, FileStrategy.BY_CODE, headerName, codeValue), "
                            + "or csvFileByCode(path) with -D"
                            + PROP_CODE_HEADER + " / -D" + PROP_CODE_VALUE + ".");
        }
        String validatedPath = validateResourcePath(resourcePath);
        FeederBuilder<String> builder = csv(validatedPath);
        return applyStrategy(builder, strategy);
    }

    /**
     * Creates a CSV feeder that yields only the row(s) where {@code headerName} equals {@code codeValue}.
     * Matching rows are fed circularly so multiple VUs can reuse them.
     *
     * @param resourcePath classpath-relative CSV path
     * @param headerName   CSV header column used as the lookup key (e.g. {@code businessTypeCode})
     * @param codeValue    expected cell value (e.g. {@code SOLE_TRADER})
     * @return feeder containing only the matching row(s)
     * @throws IllegalArgumentException if no row matches, or header/code is blank
     */
    public static FeederBuilder<Object> csvFileByCode(String resourcePath, String headerName, String codeValue) {
        return byCodeFeeder(resourcePath, headerName, codeValue);
    }

    /**
     * Creates a {@link FileStrategy#BY_CODE} CSV feeder using system properties
     * {@value #PROP_CODE_HEADER} and {@value #PROP_CODE_VALUE}.
     *
     * @param resourcePath classpath-relative CSV path
     * @return feeder containing only the matching row(s)
     */
    public static FeederBuilder<Object> csvFileByCode(String resourcePath) {
        return csvFileByCode(
                resourcePath,
                requireProp(PROP_CODE_HEADER, "BY_CODE requires -D" + PROP_CODE_HEADER + "=..."),
                requireProp(PROP_CODE_VALUE, "BY_CODE requires -D" + PROP_CODE_VALUE + "=..."));
    }

    /**
     * Creates a CSV feeder for {@link FileStrategy#BY_CODE} using the given header/code.
     * Other strategies are rejected — use {@link #csvFile(String, FileStrategy)} for those.
     *
     * @param resourcePath classpath-relative path to the CSV file
     * @param strategy     must be {@link FileStrategy#BY_CODE}
     * @param headerName   CSV header for lookup
     * @param codeValue    cell value for lookup
     * @return configured CSV feeder with matching row(s)
     */
    public static FeederBuilder<Object> csvFile(
            String resourcePath, FileStrategy strategy, String headerName, String codeValue) {
        if (strategy != FileStrategy.BY_CODE) {
            throw new IllegalArgumentException(
                    "headerName/codeValue overload is only for FileStrategy.BY_CODE. "
                            + "Use csvFile(path, strategy) for RANDOM/CIRCULAR/QUEUE.");
        }
        return byCodeFeeder(resourcePath, headerName, codeValue);
    }

    /**
     * Creates a JSON file-based feeder with default {@link FileStrategy#RANDOM} strategy.
     *
     * @param resourcePath classpath-relative path to the JSON file (e.g. {@code data/gameJsonFile.json})
     * @return configured JSON feeder
     * @throws IllegalArgumentException if the path is null or blank
     */
    public static FeederBuilder<Object> jsonFileRandom(String resourcePath) {
        return jsonFile(resourcePath, FileStrategy.RANDOM);
    }

    /**
     * Creates a JSON file-based feeder with the given strategy.
     *
     * @param resourcePath classpath-relative path to the JSON file (e.g. {@code data/gameJsonFile.json})
     * @param strategy     feeding strategy (RANDOM, CIRCULAR, QUEUE)
     * @return configured JSON feeder
     * @throws IllegalArgumentException if the path is null or blank, or strategy is BY_CODE
     */
    public static FeederBuilder<Object> jsonFile(String resourcePath, FileStrategy strategy) {
        if (strategy == FileStrategy.BY_CODE) {
            throw new IllegalArgumentException(
                    "FileStrategy.BY_CODE is only supported for CSV feeders. Use csvFile(...).");
        }
        String validatedPath = validateResourcePath(resourcePath);
        FeederBuilder.FileBased<Object> builder =
                io.gatling.javaapi.core.CoreDsl.jsonFile(validatedPath);
        return applyStrategy(builder, strategy);
    }

    private static FeederBuilder<Object> byCodeFeeder(
            String resourcePath, String headerName, String codeValue) {
        String validatedPath = validateResourcePath(resourcePath);
        if (headerName == null || headerName.isBlank()) {
            throw new IllegalArgumentException("BY_CODE headerName must not be null or blank.");
        }
        if (codeValue == null || codeValue.isBlank()) {
            throw new IllegalArgumentException("BY_CODE codeValue must not be null or blank.");
        }

        List<Map<String, Object>> matches = loadMatchingRows(validatedPath, headerName.trim(), codeValue.trim());
        if (matches.isEmpty()) {
            String msg = "No CSV row in \"" + validatedPath + "\" where column \""
                    + headerName + "\" equals \"" + codeValue + "\".";
            LOGGER.error("{}", msg);
            throw new IllegalArgumentException(msg);
        }

        LOGGER.info(
                "BY_CODE feeder \"{}\": {} row(s) where {}={}",
                validatedPath,
                matches.size(),
                headerName,
                codeValue);
        return listFeeder(matches).circular();
    }

    private static List<Map<String, Object>> loadMatchingRows(
            String resourcePath, String headerName, String codeValue) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = FeederFactory.class.getClassLoader();
        }
        try (InputStream in = Objects.requireNonNull(
                cl.getResourceAsStream(resourcePath),
                () -> "CSV resource not found on classpath: " + resourcePath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalArgumentException("CSV \"" + resourcePath + "\" has no header row.");
            }
            // strip UTF-8 BOM if present
            if (headerLine.charAt(0) == '\uFEFF') {
                headerLine = headerLine.substring(1);
            }

            String[] headers = splitCsvLine(headerLine);
            int codeIndex = -1;
            for (int i = 0; i < headers.length; i++) {
                if (headerName.equals(headers[i].trim())) {
                    codeIndex = i;
                    break;
                }
            }
            if (codeIndex < 0) {
                throw new IllegalArgumentException(
                        "CSV \"" + resourcePath + "\" has no header \"" + headerName
                                + "\". Found: " + Arrays.toString(headers));
            }

            List<Map<String, Object>> matches = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] cells = splitCsvLine(line);
                if (codeIndex >= cells.length) {
                    continue;
                }
                if (!codeValue.equals(cells[codeIndex].trim())) {
                    continue;
                }
                Map<String, Object> row = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    String key = headers[i].trim();
                    String value = i < cells.length ? cells[i].trim() : "";
                    row.put(key, value);
                }
                matches.add(row);
            }
            return matches;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read CSV \"" + resourcePath + "\"", e);
        }
    }

    /**
     * Minimal CSV splitter: handles quoted fields with commas; does not unescape escaped quotes.
     */
    private static String[] splitCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
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

    private static String requireProp(String name, String errorMessage) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(errorMessage);
        }
        return value.trim();
    }

    private static String validateResourcePath(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            String msg = "Feeder resource path must not be null or blank.";
            LOGGER.error("{}", msg);
            throw new IllegalArgumentException(msg);
        }

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl != null && cl.getResource(resourcePath) == null) {
            // Don't fail hard here; Gatling will still attempt to resolve the resource.
            LOGGER.warn("Feeder resource \"{}\" not found on classpath at validation time. "
                    + "Please ensure the file exists under src/test/resources.", resourcePath);
        }

        return resourcePath;
    }

    private static <T> FeederBuilder<T> applyStrategy(FeederBuilder<T> builder,
                                                      FileStrategy strategy) {
        FileStrategy effectiveStrategy = (strategy != null) ? strategy : FileStrategy.CIRCULAR;
        return switch (effectiveStrategy) {
            case RANDOM -> builder.random();
            case QUEUE -> builder.queue();
            case CIRCULAR -> builder.circular();
            case BY_CODE -> throw new IllegalArgumentException(
                    "FileStrategy.BY_CODE cannot be applied to a raw file feeder. "
                            + "Use csvFile(path, FileStrategy.BY_CODE, headerName, codeValue).");
        };
    }
}
