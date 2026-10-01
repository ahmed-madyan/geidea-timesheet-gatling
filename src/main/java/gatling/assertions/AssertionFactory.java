package gatling.assertions;

import io.gatling.javaapi.core.Assertion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Factory that converts a {@link SlaConfig} into a list of Gatling {@link Assertion} objects
 * ready for use in {@code setUp(...).assertions(...)}.
 *
 * <h3>Quick start</h3>
 * <pre>{@code
 * // ① Default SLA (no config needed)
 * setUp(population)
 *     .assertions(AssertionFactory.defaultAssertions())
 *     .maxDuration(Duration.ofMinutes(10));
 *
 * // ② Custom SLA
 * SlaConfig sla = SlaConfig.builder().p95Ms(1500).minSuccessPercent(99.9).build();
 * setUp(population)
 *     .assertions(AssertionFactory.fromSla(sla))
 *     .maxDuration(Duration.ofMinutes(sla.maxDurationMinutes));
 *
 * // ③ Named request assertion on top of global SLA
 * List<Assertion> assertions = new ArrayList<>(Arrays.asList(AssertionFactory.defaultAssertions()));
 * assertions.addAll(AssertionFactory.forRequest("Login Request", SlaConfig.defaultSla()));
 * setUp(population).assertions(assertions.toArray(new Assertion[0]));
 * }</pre>
 */
public final class AssertionFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(AssertionFactory.class);

    private AssertionFactory() {
        throw new UnsupportedOperationException("AssertionFactory is a utility class.");
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Shortcut: returns global assertions using {@link SlaConfig#defaultSla()}.
     * The returned array is safe to spread directly into
     * {@code setUp(...).assertions(...)}.
     */
    public static Assertion[] defaultAssertions() {
        return fromSla(SlaConfig.defaultSla());
    }

    /**
     * Builds a comprehensive set of <strong>global</strong> Gatling assertions
     * derived from the provided {@link SlaConfig}.
     *
     * <p>Assertions produced:
     * <ol>
     *   <li>p95 response time &lt; {@code sla.p95Ms}</li>
     *   <li>p99 response time &lt; {@code sla.p99Ms}</li>
     *   <li>mean response time &lt; {@code sla.meanMs}</li>
     *   <li>max response time &lt; {@code sla.maxMs}</li>
     *   <li>success rate &gt; {@code sla.minSuccessPercent}</li>
     *   <li>failed requests count &lt; {@code sla.maxFailedRequests}</li>
     *   <li>per-request p95 &lt; {@code sla.p95Ms} (forAll scope)</li>
     * </ol>
     *
     * @param sla the non-null SLA configuration
     * @return array of Gatling {@link Assertion} objects
     */
    public static Assertion[] fromSla(SlaConfig sla) {
        if (sla == null) throw new IllegalArgumentException("SlaConfig must not be null");

        LOGGER.info("Building assertions from {}", sla);

        List<Assertion> assertions = new ArrayList<>();

        // --- Global response time ---
        assertions.add(global().responseTime().percentile(95).lt(sla.p95Ms));
        assertions.add(global().responseTime().percentile(99).lt(sla.p99Ms));
        assertions.add(global().responseTime().mean().lt(sla.meanMs));
        assertions.add(global().responseTime().max().lt(sla.maxMs));

        // --- Global success / failure rate ---
        assertions.add(global().successfulRequests().percent().gt(sla.minSuccessPercent));
        assertions.add(global().failedRequests().count().lt(sla.maxFailedRequests));

        // --- Per-request (forAll) p95 cap ---
        assertions.add(forAll().responseTime().percentile(95).lt(sla.p95Ms));

        LOGGER.info("Created {} global + forAll assertions.", assertions.size());
        return assertions.toArray(new Assertion[0]);
    }

    /**
     * Adds named-request assertions for a specific request (identified by its
     * Gatling request label) on top of the provided SLA.
     *
     * <p>Assertions produced:
     * <ol>
     *   <li>Mean response time &lt; {@code sla.meanMs}</li>
     *   <li>p99 response time &lt; {@code sla.p99Ms}</li>
     *   <li>Failed requests percent &lt; 1%</li>
     * </ol>
     *
     * @param requestName the exact request label used in the simulation
     * @param sla         the SLA to apply
     * @return list of per-request assertions (add to your full assertions list)
     */
    public static List<Assertion> forRequest(String requestName, SlaConfig sla) {
        if (requestName == null || requestName.isBlank())
            throw new IllegalArgumentException("requestName must not be null or blank");
        if (sla == null)
            throw new IllegalArgumentException("SlaConfig must not be null");

        LOGGER.info("Building per-request assertions for '{}' from {}", requestName, sla);

        return List.of(
                details(requestName).responseTime().mean().lt(sla.meanMs),
                details(requestName).responseTime().percentile(99).lt(sla.p99Ms),
                details(requestName).failedRequests().percent().lt(1.0)
        );
    }

    /**
     * Convenience method: builds a strict SLA with zero tolerance for failures.
     * Useful for smoke/sanity tests.
     *
     * <p>Thresholds: p95 &lt; 1 000 ms, success = 100%.
     */
    public static Assertion[] strictAssertions() {
        SlaConfig strict = SlaConfig.builder()
                .p95Ms(1000)
                .p99Ms(2000)
//                .meanMs(500)
//                .maxMs(5000)
                .minSuccessPercent(100.0)
                .maxFailedRequests(0L)
                .build();
        return fromSla(strict);
    }
}
