package gatling.utils;

import gatling.builders.CsvFileBuilder;
import gatling.config.env.EnvConfig;
import gatling.config.region.RegionConfig;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.CheckBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.gatling.javaapi.core.CoreDsl.jsonPath;

/**
 * Helper for capturing key fields from the Apex paymentGateway
 * {@code POST BasePath.ksa.PGW_DIRECT_PAY} ({@code /pgw/api/v2/direct/pay}) response into the Gatling session and persisting
 * one row per virtual user to a CSV file via {@link CsvFileBuilder}.
 *
 * <p>The default {@link #DEFAULT_PAY_COLUMNS column set} targets {@code transactions[1]}
 * of the Pay response (the {@code Pay} transaction itself; index {@code [0]} is the prior
 * {@code Authentication} transaction). All extractors use {@code ofString().withDefault("")}
 * so nullable fields ({@code authorizationCode}, {@code rrn}, {@code acquirer.id} on failed
 * transactions) do not break the request.
 *
 * <p>Typical usage on a Pay request — capture checks attached to the HTTP call, then a
 * separate {@code exec} step appends the CSV row using the captured session values:
 * <pre>{@code
 *   public static ChainBuilder pay() {
 *       ChainBuilderFactory factory = new ChainBuilderFactory("4. Pay")
 *               .post(BasePath.ksa.PGW_DIRECT_PAY)
 *               .withHeader("Content-Type", "application/json")
 *               .withBodyFromFile(BODY_PREFIX + "direct_pay.json")
 *               .withStatusCheck(StatusCode.SC_OK)
 *               .withJsonPathCheck("$.responseCode", "000")
 *               .withChecks(TransactionRecorder.captureChecks().toArray(new CheckBuilder[0]));
 *       return factory.build();
 *   }
 *
 *   public static ChainBuilder build() {
 *       return exec(
 *           // ... earlier steps ...
 *           pay(),
 *           TransactionRecorder.appendCsvRow()
 *       );
 *   }
 * }</pre>
 *
 * <p><strong>Output file</strong> defaults to
 * {@code data/{region}/{env}/apex/created_transactions.csv} (relative to the working
 * directory the JVM is launched from — i.e. the project root for {@code mvn gatling:test}).
 * Override with {@code -DtransactionRecorderCsv=path/to/out.csv}. The parent directory is
 * created if it does not exist; the header row is emitted on the first write only.
 */
public final class TransactionRecorder {

    private static final Logger LOG = LoggerFactory.getLogger(TransactionRecorder.class);

    /**
     * Column descriptor: {@link #header} appears once in the CSV header, {@link #jsonPath}
     * is the Gatling/Jayway JSONPath used to extract the value from the response, and
     * {@link #sessionKey} is the Gatling session attribute the value is stored in.
     */
    public record Column(String header, String jsonPath, String sessionKey) {
    }

    /**
     * Default columns matching the requested mapping for the Pay response. The transaction
     * index {@code [1]} targets the actual Pay transaction (index {@code [0]} is the prior
     * Authentication transaction).
     */
    public static final List<Column> DEFAULT_PAY_COLUMNS = List.of(
            new Column("orderId",            "$.order.orderId",                                          "txn_orderId"),
            new Column("amount",             "$.order.amount",                                           "txn_amount"),
            new Column("merchantId",         "$.order.merchantId",                                       "txn_merchantId"),
            new Column("merchantPublicKey",  "$.order.merchantPublicKey",                                "txn_merchantPublicKey"),
            new Column("transactionId",      "$.order.transactions[1].transactionId",                    "txn_transactionId"),
            new Column("status",             "$.order.transactions[1].status",                           "txn_status"),
            new Column("authorizationCode",  "$.order.transactions[1].authorizationCode",                "txn_authorizationCode"),
            new Column("rrn",                "$.order.transactions[1].rrn",                              "txn_rrn"),
            new Column("paymentMethodBrand", "$.order.transactions[1].paymentMethod.brand",              "txn_paymentMethodBrand"),
            new Column("cardholderName",     "$.order.transactions[1].paymentMethod.cardholderName",     "txn_cardholderName"),
            new Column("maskedCardNumber",   "$.order.transactions[1].paymentMethod.maskedCardNumber",   "txn_maskedCardNumber"),
            new Column("acquirer",           "$.order.transactions[1].acquirer.id",                      "txn_acquirer")
    );

    /**
     * Default output CSV path, mirrors the input feeder layout
     * ({@code data/{region}/{env}/apex/...}). Override via
     * {@code -DtransactionRecorderCsv=...}.
     */
    public static final String DEFAULT_CSV_PATH = System.getProperty(
            "transactionRecorderCsv",
            "src/test/resources/data/" + RegionConfig.active().code()
                    + "/" + EnvConfig.active().name()
                    + "/apex/created_transactions.csv");

    private TransactionRecorder() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns Gatling {@link CheckBuilder}s that extract every {@link #DEFAULT_PAY_COLUMNS}
     * value into the session. Pass to a request via
     * {@code .withChecks(captureChecks().toArray(new CheckBuilder[0]))}.
     */
    public static List<CheckBuilder> captureChecks() {
        return captureChecks(DEFAULT_PAY_COLUMNS);
    }

    /**
     * Same as {@link #captureChecks()} but for a custom column set (e.g. a Capture/Refund/Void
     * response with a different transactions index).
     */
    public static List<CheckBuilder> captureChecks(List<Column> columns) {
        List<CheckBuilder> checks = new ArrayList<>(columns.size());
        for (Column c : columns) {
            checks.add(jsonPath(c.jsonPath()).ofString().withDefault("").saveAs(c.sessionKey()));
        }
        return checks;
    }

    /**
     * Appends a single CSV row using values previously captured by {@link #captureChecks()}.
     * Writes to {@link #DEFAULT_CSV_PATH}, creating parent directories and emitting the
     * header on first write.
     */
    public static ChainBuilder appendCsvRow() {
        return appendCsvRow(DEFAULT_CSV_PATH, DEFAULT_PAY_COLUMNS);
    }

    /**
     * Appends to the given path using the default Pay column set.
     */
    public static ChainBuilder appendCsvRow(String csvPath) {
        return appendCsvRow(csvPath, DEFAULT_PAY_COLUMNS);
    }

    /**
     * Appends to the given path using a custom column set. Header is written once, on first
     * append. Per-file write lock is provided by {@link CsvFileBuilder}.
     */
    public static ChainBuilder appendCsvRow(String csvPath, List<Column> columns) {
        LOG.debug("TransactionRecorder appending via CsvFileBuilder to {}", csvPath);
        CsvFileBuilder.Column[] csvColumns = columns.stream()
                .map(c -> CsvFileBuilder.column(c.header(), c.sessionKey()))
                .toArray(CsvFileBuilder.Column[]::new);
        return CsvFileBuilder.to(csvPath)
                .columns(csvColumns)
                .appendFromSession();
    }
}
