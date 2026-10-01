package gatling.builders;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.gatling.javaapi.core.CoreDsl.exec;

/**
 * Framework-wide CSV file builder for Gatling chain {@code build()} flows.
 *
 * <p>Centralises RFC-4180 escaping, per-file locking, header-on-first-write, optional stale-header
 * rewrite, and optional auto {@code index} columns so chain classes do not duplicate CSV I/O.
 *
 * <p>Typical usage from a generic chain {@code build()}:
 * <pre>{@code
 *   public static ChainBuilder appendMerchantCsvRow() {
 *       return CsvFileBuilder.to(resolveCsvPath())
 *               .columns(
 *                       CsvFileBuilder.column("businessId", "parentBusinessId"),
 *                       CsvFileBuilder.column("accountMid", "subMerchantId"),
 *                       CsvFileBuilder.column("terminalTid", "terminalTid"),
 *                       CsvFileBuilder.column("terminalFullTid", "terminalFullTid"),
 *                       CsvFileBuilder.column("terminalMid", "terminalMid"),
 *                       CsvFileBuilder.column("contractStartDate", "contractStartDate"))
 *               .appendFromSession();
 *   }
 * }</pre>
 *
 * <p>Concurrency: writes are serialized per absolute path via a {@link ConcurrentHashMap}-keyed
 * lock so multiple virtual users in the same JVM cannot interleave bytes. Cross-JVM writes to the
 * same path are not coordinated.
 *
 * <p>Open-while-running: all appends go to a sidecar {@code <file>.writing} (source of truth), so
 * logging never depends on whether Excel/IDE has a CSV open. A best-effort live preview is
 * published to {@code <file>.preview.csv} (open that for mid-run inspection — not {@code <file>}
 * itself). On JVM shutdown the sidecar is force-promoted to the logical {@code <file>} with
 * retries; if the logical path stays locked, data is saved as {@code <file>.complete}.
 * Cadence: {@code -DcsvPublishEveryRows}, {@code -DcsvPublishEveryMs}. Headerless appends use
 * {@link #appendRawLine(Path, String)}.
 */
public final class CsvFileBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(CsvFileBuilder.class);

    private static final int PUBLISH_EVERY_ROWS =
            Integer.getInteger("csvPublishEveryRows", 500);
    private static final long PUBLISH_EVERY_MS =
            Long.getLong("csvPublishEveryMs", 2_000L);
    private static final int APPEND_RETRIES =
            Integer.getInteger("csvAppendRetries", 5);
    private static final int FINALIZE_RETRIES =
            Integer.getInteger("csvFinalizeRetries", 60);
    private static final long FINALIZE_RETRY_MS =
            Long.getLong("csvFinalizeRetryMs", 500L);
    private static final int PROGRESS_LOG_EVERY_ROWS =
            Integer.getInteger("csvProgressLogEveryRows", 5_000);

    private static final ConcurrentMap<Path, Object> FILE_LOCKS = new ConcurrentHashMap<>();
    /** Logical path → long-lived writer (opened against the {@code .writing} sidecar). */
    private static final ConcurrentMap<Path, BufferedWriter> APPEND_WRITERS = new ConcurrentHashMap<>();
    /** Logical paths known to already contain a header in the working file. */
    private static final ConcurrentMap<Path, Boolean> INITIALIZED_PATHS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Path, Integer> ROWS_SINCE_PUBLISH = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Path, Long> LAST_PUBLISH_MS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<Path, Long> ROWS_WRITTEN = new ConcurrentHashMap<>();

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(CsvFileBuilder::closeAllWriters, "csv-file-builder-shutdown"));
    }

    private CsvFileBuilder() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Column descriptor: {@link #header} appears in the CSV header; {@link #sessionKeys} are
     * tried in order and the first non-blank session value is written.
     */
    public record Column(String header, String... sessionKeys) {
        public Column {
            Objects.requireNonNull(header, "header");
            if (sessionKeys == null || sessionKeys.length == 0) {
                throw new IllegalArgumentException("sessionKeys must not be empty for column: " + header);
            }
        }
    }

    /** Creates a column that reads a single session key (header defaults to the same name). */
    public static Column column(String sessionKey) {
        return new Column(sessionKey, sessionKey);
    }

    /** Creates a column with an explicit header and one or more session-key fallbacks. */
    public static Column column(String header, String primaryKey, String... fallbackKeys) {
        String[] keys = new String[1 + (fallbackKeys == null ? 0 : fallbackKeys.length)];
        keys[0] = primaryKey;
        if (fallbackKeys != null && fallbackKeys.length > 0) {
            System.arraycopy(fallbackKeys, 0, keys, 1, fallbackKeys.length);
        }
        return new Column(header, keys);
    }

    /** Starts a fluent CSV write specification for {@code path}. */
    public static Spec to(Path path) {
        return new Spec(path);
    }

    /** Starts a fluent CSV write specification for {@code path}. */
    public static Spec to(String path) {
        return new Spec(Paths.get(path));
    }

    /**
     * Starts a fluent CSV write specification whose path is resolved from the Gatling
     * session at append time (not at chain {@code build()} time).
     *
     * <p>Use this when the destination folder/file depends on a feeder value such as
     * {@code acquirerBankName}. Gatling EL ({@code #{...}}) is not interpolated in Java
     * {@link Path} construction.
     */
    public static Spec to(Function<Session, Path> pathFromSession) {
        return new Spec(pathFromSession);
    }

    /**
     * Fluent write specification. Configure headers/columns then call
     * {@link #appendFromSession()} from a chain {@code build()}.
     */
    public static final class Spec {
        private final Path staticPath;
        private final Function<Session, Path> pathResolver;
        private final List<Column> columns = new ArrayList<>();
        private String headerLine;
        private boolean rewriteStaleHeader;
        private boolean autoIndex;
        private String indexHeader = "index";

        private Spec(Path path) {
            this.staticPath = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
            this.pathResolver = session -> this.staticPath;
        }

        private Spec(Function<Session, Path> pathFromSession) {
            this.staticPath = null;
            Function<Session, Path> resolver = Objects.requireNonNull(pathFromSession, "pathFromSession");
            this.pathResolver = session -> Objects.requireNonNull(resolver.apply(session), "path")
                    .toAbsolutePath()
                    .normalize();
        }

        private Path resolvePath(Session session) {
            return pathResolver.apply(session);
        }

        /**
         * Sets the exact header line (comma-joined). Prefer {@link #columns(Column...)} when
         * reading from the Gatling session; use this when supplying a pre-built data row.
         */
        public Spec header(String headerLine) {
            this.headerLine = Objects.requireNonNull(headerLine, "headerLine");
            return this;
        }

        /** Sets headers from discrete names (joined with commas). */
        public Spec headers(String... headers) {
            if (headers == null || headers.length == 0) {
                throw new IllegalArgumentException("headers must not be empty");
            }
            this.headerLine = String.join(",", headers);
            return this;
        }

        /**
         * Declares session-backed columns. Also derives the header line from each
         * {@link Column#header()} unless {@link #header(String)} was already set.
         */
        public Spec columns(Column... columns) {
            if (columns == null || columns.length == 0) {
                throw new IllegalArgumentException("columns must not be empty");
            }
            this.columns.clear();
            this.columns.addAll(Arrays.asList(columns));
            if (this.headerLine == null) {
                this.headerLine = Arrays.stream(columns)
                        .map(Column::header)
                        .collect(Collectors.joining(","));
            }
            return this;
        }

        /**
         * When the file exists but line 1 is not the expected header, rewrite the file so the
         * correct header precedes preserved data rows (merchant export behaviour).
         */
        public Spec rewriteStaleHeader(boolean enabled) {
            this.rewriteStaleHeader = enabled;
            return this;
        }

        /**
         * Prepends a 1-based {@code index} column computed from the current file length.
         * Must be used with {@link #rewriteStaleHeader(boolean)} for merchant CSVs that track index.
         */
        public Spec withAutoIndex() {
            return withAutoIndex("index");
        }

        /** Same as {@link #withAutoIndex()} with a custom index column header. */
        public Spec withAutoIndex(String indexHeader) {
            this.autoIndex = true;
            this.indexHeader = Objects.requireNonNull(indexHeader, "indexHeader");
            return this;
        }

        /**
         * Gatling {@code exec} step that appends one row from session values described by
         * {@link #columns(Column...)}.
         */
        public ChainBuilder appendFromSession() {
            ensureConfiguredForSession();
            return exec(session -> {
                Path path = resolvePath(session);
                try {
                    appendSessionRow(session, path);
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed appending CSV row to " + path, e);
                }
                return session;
            });
        }

        /**
         * Appends one row resolved from {@code session} using the configured columns.
         * Thread-safe for concurrent VUs writing to the same path in this JVM.
         */
        public void appendSessionRow(Session session) throws IOException {
            appendSessionRow(session, resolvePath(session));
        }

        private void appendSessionRow(Session session, Path path) throws IOException {
            ensureConfiguredForSession();
            Object lock = FILE_LOCKS.computeIfAbsent(path, p -> new Object());
            synchronized (lock) {
                String expectedHeader = resolvedHeader();
                List<String> cells = new ArrayList<>(columns.size() + (autoIndex ? 1 : 0));
                if (autoIndex) {
                    long index = resolveNextIndex(path, expectedHeader);
                    cells.add(escape(Long.toString(index)));
                }
                for (Column column : columns) {
                    cells.add(cell(session, column.sessionKeys()[0],
                            Arrays.copyOfRange(column.sessionKeys(), 1, column.sessionKeys().length)));
                }
                String dataRow = String.join(",", cells);
                writeDataRow(path, expectedHeader, dataRow, rewriteStaleHeader);
                LOG.debug("CsvFileBuilder appended row to {}", path);
            }
        }

        /**
         * Appends a pre-built data row (already escaped / joined). Uses {@link #header(String)}
         * (or {@link #headers(String...)}) for the header line.
         */
        public void appendDataRow(String dataRow) throws IOException {
            if (headerLine == null || headerLine.isBlank()) {
                throw new IllegalStateException("header must be set before appendDataRow()");
            }
            if (staticPath == null) {
                throw new IllegalStateException("appendDataRow requires CsvFileBuilder.to(Path) or to(String)");
            }
            Object lock = FILE_LOCKS.computeIfAbsent(staticPath, p -> new Object());
            synchronized (lock) {
                writeDataRow(staticPath, headerLine, dataRow, rewriteStaleHeader);
                LOG.debug("CsvFileBuilder appended data row to {}", staticPath);
            }
        }

        private void ensureConfiguredForSession() {
            if (columns.isEmpty()) {
                throw new IllegalStateException("columns must be set before appending from session");
            }
        }

        private String resolvedHeader() {
            if (headerLine != null && !headerLine.isBlank()) {
                if (autoIndex && !headerLine.startsWith(indexHeader + ",")) {
                    return indexHeader + "," + headerLine;
                }
                return headerLine;
            }
            String fromColumns = columns.stream()
                    .map(Column::header)
                    .collect(Collectors.joining(","));
            return autoIndex ? indexHeader + "," + fromColumns : fromColumns;
        }
    }

    // ── Session / escaping helpers ────────────────────────────────────────────

    /**
     * Resolves a CSV cell from the first non-blank session value among {@code primaryKey} and
     * {@code fallbackKeys}.
     */
    public static String cell(Session session, String primaryKey, String... fallbackKeys) {
        if (isPresent(session, primaryKey)) {
            return escape(session.getString(primaryKey).trim());
        }
        if (fallbackKeys != null) {
            for (String key : fallbackKeys) {
                if (isPresent(session, key)) {
                    return escape(session.getString(key).trim());
                }
            }
        }
        return escape("");
    }

    /** Joins already-escaped cells with commas. */
    public static String join(String... escapedCells) {
        return String.join(",", escapedCells);
    }

    /**
     * Minimal RFC-4180-style escaping: wrap in quotes if the value contains a comma, quote,
     * CR or LF; double-up embedded quotes.
     */
    public static String escape(String raw) {
        if (raw == null) {
            return "";
        }
        boolean needQuotes = raw.indexOf(',') >= 0
                || raw.indexOf('"') >= 0
                || raw.indexOf('\n') >= 0
                || raw.indexOf('\r') >= 0;
        String escaped = raw.replace("\"", "\"\"");
        return needQuotes ? "\"" + escaped + "\"" : escaped;
    }

    // ── Low-level file I/O (also usable without Spec) ─────────────────────────

    /**
     * Appends {@code dataRow} to {@code path}, writing {@code header} first if the file is
     * missing or empty. Creates the parent directory tree on demand. Per-file lock is applied.
     */
    public static void appendRow(Path path, String header, String dataRow) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Object lock = FILE_LOCKS.computeIfAbsent(absolute, p -> new Object());
        synchronized (lock) {
            writeDataRow(absolute, header, dataRow, false);
        }
    }

    /**
     * Like {@link #appendRow(Path, String, String)} but rewrites the file when line 1 is missing
     * or a stale header (preserving existing data rows).
     */
    public static void appendRowRewritingStaleHeader(Path path, String header, String dataRow)
            throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Object lock = FILE_LOCKS.computeIfAbsent(absolute, p -> new Object());
        synchronized (lock) {
            writeDataRow(absolute, header, dataRow, true);
        }
    }

    /**
     * Appends a raw line (no CSV header handling) using the same long-lived writer, sidecar, and
     * per-path lock as CSV exports. Use for high-volume non-CSV append files such as PTLF.
     */
    public static void appendRawLine(Path path, String line) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Object lock = FILE_LOCKS.computeIfAbsent(absolute, p -> new Object());
        synchronized (lock) {
            Files.createDirectories(parentOrThrow(absolute));
            seedWorkingFromLogical(absolute);
            appendLine(absolute, line);
            INITIALIZED_PATHS.put(absolute, Boolean.TRUE);
            noteRowWritten(absolute);
            publishBestEffort(absolute, false);
        }
    }

    /**
     * Next 1-based index column for an appended row, based on current line count and whether
     * the first line already matches {@code expectedHeader}. Prefers the {@code .writing}
     * sidecar when present.
     */
    public static long resolveNextIndex(Path path, String expectedHeader) throws IOException {
        Path absolute = path.toAbsolutePath().normalize();
        Path source = readableSource(absolute);
        if (!Files.exists(source) || Files.size(source) == 0) {
            return 1L;
        }
        List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
        if (lines.isEmpty()) {
            return 1L;
        }
        String first = stripBom(lines.get(0));
        if (first.equals(expectedHeader)) {
            return lines.size();
        }
        if (first.startsWith("index,") && !first.equals(expectedHeader)) {
            return lines.size();
        }
        return lines.size() + 1L;
    }

    private static void writeDataRow(Path logical, String expectedHeader, String dataRow,
                                     boolean rewriteStaleHeader) throws IOException {
        Files.createDirectories(parentOrThrow(logical));
        seedWorkingFromLogical(logical);
        Path working = workingPath(logical);

        boolean initialized = INITIALIZED_PATHS.containsKey(logical);
        if (!initialized && (!Files.exists(working) || Files.size(working) == 0)) {
            closeWriter(logical);
            writeAllLines(working, List.of(expectedHeader, dataRow));
            INITIALIZED_PATHS.put(logical, Boolean.TRUE);
            noteRowWritten(logical);
            publishBestEffort(logical, true);
            return;
        }
        if (!rewriteStaleHeader) {
            appendLine(logical, dataRow);
            INITIALIZED_PATHS.put(logical, Boolean.TRUE);
            noteRowWritten(logical);
            publishBestEffort(logical, false);
            return;
        }
        closeWriter(logical);
        List<String> lines = Files.exists(working) && Files.size(working) > 0
                ? Files.readAllLines(working, StandardCharsets.UTF_8)
                : List.of();
        if (lines.isEmpty()) {
            writeAllLines(working, List.of(expectedHeader, dataRow));
            INITIALIZED_PATHS.put(logical, Boolean.TRUE);
            noteRowWritten(logical);
            publishBestEffort(logical, true);
            return;
        }
        String first = stripBom(lines.get(0));
        if (first.equals(expectedHeader)) {
            appendLine(logical, dataRow);
            INITIALIZED_PATHS.put(logical, Boolean.TRUE);
            noteRowWritten(logical);
            publishBestEffort(logical, false);
            return;
        }
        List<String> out = new ArrayList<>(lines.size() + 2);
        out.add(expectedHeader);
        if (first.startsWith("index,") && !first.equals(expectedHeader)) {
            out.addAll(lines.subList(1, lines.size()));
        } else {
            out.addAll(lines);
        }
        out.add(dataRow);
        writeAllLines(working, out);
        INITIALIZED_PATHS.put(logical, Boolean.TRUE);
        noteRowWritten(logical);
        publishBestEffort(logical, true);
    }

    /**
     * Appends one line via a cached writer to the {@code .writing} sidecar. Reopens and retries
     * on transient I/O failures (e.g. editor briefly locking the working file).
     * Caller must hold the per-path lock.
     */
    private static void appendLine(Path logical, String dataRow) throws IOException {
        Path working = workingPath(logical);
        IOException last = null;
        for (int attempt = 1; attempt <= APPEND_RETRIES; attempt++) {
            try {
                BufferedWriter w = APPEND_WRITERS.get(logical);
                if (w == null) {
                    w = newUtf8AppendWriter(working);
                    APPEND_WRITERS.put(logical, w);
                }
                w.write(dataRow);
                w.newLine();
                w.flush();
                return;
            } catch (IOException e) {
                last = e;
                closeWriter(logical);
                LOG.warn("CSV append failed for {} (attempt {}/{}): {}",
                        working, attempt, APPEND_RETRIES, e.toString());
                if (attempt < APPEND_RETRIES) {
                    try {
                        Thread.sleep(25L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw e;
                    }
                }
            }
        }
        throw last != null ? last : new IOException("CSV append failed for " + working);
    }

    private static BufferedWriter newUtf8AppendWriter(Path path) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(
                Files.newOutputStream(path, StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND),
                StandardCharsets.UTF_8),
                64 * 1024);
    }

    private static void writeAllLines(Path path, List<String> lines) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
            for (String line : lines) {
                w.write(line);
                w.newLine();
            }
            w.flush();
        }
    }

    /**
     * Best-effort refresh of {@code <file>.preview.csv} from the sidecar. The logical {@code <file>}
     * is NOT touched mid-run (so opening it in Excel cannot freeze the snapshot or block writers).
     * Failures are ignored — appends keep going to {@code .writing}.
     */
    private static void publishBestEffort(Path logical, boolean force) {
        Path working = workingPath(logical);
        if (!Files.exists(working)) {
            return;
        }
        int rows = ROWS_SINCE_PUBLISH.merge(logical, 1, Integer::sum);
        long now = System.currentTimeMillis();
        long last = LAST_PUBLISH_MS.getOrDefault(logical, 0L);
        if (!force && rows < PUBLISH_EVERY_ROWS && (now - last) < PUBLISH_EVERY_MS) {
            return;
        }

        flushWriterQuietly(logical);

        Path preview = previewPath(logical);
        Path publishTmp = logical.resolveSibling(logical.getFileName().toString() + ".publish-tmp");
        try {
            Files.copy(working, publishTmp, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(publishTmp, preview,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(publishTmp, preview, StandardCopyOption.REPLACE_EXISTING);
            }
            ROWS_SINCE_PUBLISH.put(logical, 0);
            LAST_PUBLISH_MS.put(logical, now);
        } catch (IOException e) {
            LOG.debug("Could not refresh preview {} (file may be open): {}",
                    preview, e.toString());
            try {
                Files.deleteIfExists(publishTmp);
            } catch (IOException ignored) {
                // best effort
            }
        }
    }

    private static void noteRowWritten(Path logical) {
        long n = ROWS_WRITTEN.merge(logical, 1L, Long::sum);
        if (n == 1L || n % PROGRESS_LOG_EVERY_ROWS == 0L) {
            LOG.info("CSV rows written: {} → {} (live file). Mid-run preview: {}",
                    n, workingPath(logical), previewPath(logical));
        }
    }

    /**
     * Promotes {@code .writing} → logical path with retries. Never deletes the sidecar until the
     * logical file matches. If the logical path stays locked, saves {@code <file>.complete}.
     */
    private static void finalizeLogical(Path logical) {
        Path working = workingPath(logical);
        if (!Files.exists(working)) {
            return;
        }
        flushWriterQuietly(logical);
        closeWriter(logical);

        IOException last = null;
        for (int attempt = 1; attempt <= FINALIZE_RETRIES; attempt++) {
            try {
                copyReplace(working, logical);
                if (Files.size(working) == Files.size(logical)) {
                    Files.deleteIfExists(working);
                    Files.deleteIfExists(previewPath(logical));
                    Files.deleteIfExists(logical.resolveSibling(
                            logical.getFileName().toString() + ".publish-tmp"));
                    LOG.info("Finalized CSV {} ({} bytes)", logical, Files.size(logical));
                    return;
                }
            } catch (IOException e) {
                last = e;
                // Locked logical file: try delete then move sidecar into place.
                try {
                    Files.deleteIfExists(logical);
                    Files.move(working, logical, StandardCopyOption.REPLACE_EXISTING);
                    Files.deleteIfExists(previewPath(logical));
                    LOG.info("Finalized CSV {} by move after unlock", logical);
                    return;
                } catch (IOException e2) {
                    last = e2;
                }
            }
            if (attempt < FINALIZE_RETRIES) {
                try {
                    Thread.sleep(FINALIZE_RETRY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        Path complete = completePath(logical);
        try {
            copyReplace(working, complete);
            LOG.error(
                    "Could not finalize {} (still locked after {} retries: {}). "
                            + "FULL output is at {} — close Excel/IDE and rename it to the CSV name.",
                    logical,
                    FINALIZE_RETRIES,
                    last != null ? last.toString() : "unknown",
                    complete);
        } catch (IOException e) {
            LOG.error("CRITICAL: could not finalize {} and failed writing {}.complete — data remains in {}",
                    logical, logical.getFileName(), working, e);
        }
    }

    private static void copyReplace(Path from, Path to) throws IOException {
        Path tmp = to.resolveSibling(to.getFileName().toString() + ".publish-tmp");
        Files.copy(from, tmp, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(tmp, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void flushWriterQuietly(Path logical) {
        BufferedWriter w = APPEND_WRITERS.get(logical);
        if (w == null) {
            return;
        }
        try {
            w.flush();
        } catch (IOException e) {
            LOG.debug("Flush failed for {}: {}", logical, e.toString());
        }
    }

    /** If {@code .writing} is missing but the logical file has data, seed the sidecar from it. */
    private static void seedWorkingFromLogical(Path logical) throws IOException {
        Path working = workingPath(logical);
        if (Files.exists(working) && Files.size(working) > 0) {
            return;
        }
        Path seed = logical;
        Path complete = completePath(logical);
        // Prefer a previous .complete leftover over a stale partial logical snapshot.
        if (Files.exists(complete) && Files.size(complete) > 0) {
            if (!Files.exists(logical) || Files.size(complete) >= Files.size(logical)) {
                seed = complete;
            }
        }
        if (Files.exists(seed) && Files.size(seed) > 0) {
            Files.copy(seed, working, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path workingPath(Path logical) {
        return logical.resolveSibling(logical.getFileName().toString() + ".writing");
    }

    private static Path previewPath(Path logical) {
        return logical.resolveSibling(logical.getFileName().toString() + ".preview.csv");
    }

    private static Path completePath(Path logical) {
        return logical.resolveSibling(logical.getFileName().toString() + ".complete");
    }

    private static Path readableSource(Path logical) throws IOException {
        Path working = workingPath(logical);
        if (Files.exists(working) && Files.size(working) > 0) {
            return working;
        }
        Path complete = completePath(logical);
        if (Files.exists(complete) && Files.size(complete) > 0) {
            return complete;
        }
        return logical;
    }

    private static Path parentOrThrow(Path path) {
        Path parent = path.getParent();
        if (parent == null) {
            throw new IllegalArgumentException("Path has no parent directory: " + path);
        }
        return parent;
    }

    private static void closeWriter(Path logical) {
        BufferedWriter w = APPEND_WRITERS.remove(logical);
        if (w == null) {
            return;
        }
        try {
            w.flush();
            w.close();
        } catch (IOException e) {
            LOG.warn("Failed closing CSV append writer for {}: {}", logical, e.toString());
        }
    }

    private static void closeAllWriters() {
        Set<Path> logicalPaths = new HashSet<>();
        logicalPaths.addAll(APPEND_WRITERS.keySet());
        logicalPaths.addAll(INITIALIZED_PATHS.keySet());
        for (Path logical : logicalPaths) {
            Object lock = FILE_LOCKS.computeIfAbsent(logical, p -> new Object());
            synchronized (lock) {
                finalizeLogical(logical);
            }
        }
        APPEND_WRITERS.clear();
        INITIALIZED_PATHS.clear();
        ROWS_SINCE_PUBLISH.clear();
        LAST_PUBLISH_MS.clear();
        ROWS_WRITTEN.clear();
    }

    private static boolean isPresent(Session session, String key) {
        String value = session.getString(key);
        return value != null && !value.isBlank();
    }

    private static String stripBom(String s) {
        if (s == null) {
            return "";
        }
        return s.startsWith("\uFEFF") ? s.substring(1) : s;
    }
}
