package gatling.assertions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Immutable value object holding SLA thresholds for a Gatling simulation.
 * <p>
 * Use {@link #defaultSla()} for sensible out-of-the-box thresholds, or
 * use {@link Builder} via {@link #builder()} to override specific values
 * per simulation.
 *
 * <pre>{@code
 * // Default SLA
 * SlaConfig sla = SlaConfig.defaultSla();
 *
 * // Custom SLA
 * SlaConfig sla = SlaConfig.builder()
 *         .p95Ms(1500)
 *         .minSuccessPercent(99.9)
 *         .maxDurationMinutes(15)
 *         .build();
 * }</pre>
 */
public final class SlaConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlaConfig.class);

    // -------------------------------------------------------------------------
    // Fields (all in milliseconds unless stated otherwise)
    // -------------------------------------------------------------------------

    /**
     * p95 response time ceiling (ms). Gatling assertion fails if exceeded.
     */
    public final int p95Ms;

    /**
     * p99 response time ceiling (ms).
     */
    public final int p99Ms;

    /**
     * Mean response time ceiling across all requests (ms).
     */
    public final int meanMs;

    /**
     * Absolute max response time for any single request (ms).
     */
    public final int maxMs;

    /**
     * Minimum percentage of successful requests (0-100).
     */
    public final double minSuccessPercent;

    /**
     * Maximum number of allowed failed requests (absolute count).
     */
    public final long maxFailedRequests;

    /**
     * Hard safety timeout for the entire simulation (minutes).
     */
    public final int maxDurationMinutes;

    // -------------------------------------------------------------------------
    // Private constructor — use defaultSla() or builder()
    // -------------------------------------------------------------------------

    private SlaConfig(Builder b) {
        this.p95Ms = b.p95Ms;
        this.p99Ms = b.p99Ms;
        this.meanMs = b.meanMs;
        this.maxMs = b.maxMs;
        this.minSuccessPercent = b.minSuccessPercent;
        this.maxFailedRequests = b.maxFailedRequests;
        this.maxDurationMinutes = b.maxDurationMinutes;

        LOGGER.info("SlaConfig created — p95={}ms, p99={}ms, mean={}ms, max={}ms, "
                        + "minSuccess={}%, maxFailed={}, maxDuration={}min",
                p95Ms, p99Ms, meanMs, maxMs, minSuccessPercent,
                maxFailedRequests, maxDurationMinutes);
    }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    /**
     * Returns a pre-configured SLA suitable for most REST API performance tests:
     * <ul>
     *   <li>p95 &lt; 2 000 ms</li>
     *   <li>p99 &lt; 5 000 ms</li>
     *   <li>mean &lt; 1 000 ms</li>
     *   <li>max &lt; 10 000 ms</li>
     *   <li>success rate &gt; 99 %</li>
     *   <li>failed requests &lt; 10</li>
     *   <li>max simulation duration: 10 min</li>
     * </ul>
     */
    public static SlaConfig defaultSla() {
        return builder().build();
    }

    /**
     * Returns a new {@link Builder} pre-populated with the default values.
     */
    public static Builder builder() {
        return new Builder();
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static final class Builder {

        private int p95Ms = 3000;
        private int p99Ms = 5000;
        private int meanMs = 2000;
        private int maxMs = 10000;
        private double minSuccessPercent = 99.0;
        private long maxFailedRequests = 10;
        private int maxDurationMinutes = 30;

        private Builder() {
        }

        /**
         * p95 response time ceiling in milliseconds (default: 2 000).
         */
        public Builder p95Ms(int val) {
            if (val <= 0) throw new IllegalArgumentException("p95Ms must be > 0");
            this.p95Ms = val;
            return this;
        }

        /**
         * p99 response time ceiling in milliseconds (default: 5 000).
         */
        public Builder p99Ms(int val) {
            if (val <= 0) throw new IllegalArgumentException("p99Ms must be > 0");
            this.p99Ms = val;
            return this;
        }

        /**
         * Mean response time ceiling in milliseconds (default: 1 000).
         */
        public Builder meanMs(int val) {
            if (val <= 0) throw new IllegalArgumentException("meanMs must be > 0");
            this.meanMs = val;
            return this;
        }

        /**
         * Absolute max response time ceiling in milliseconds (default: 10 000).
         */
        public Builder maxMs(int val) {
            if (val <= 0) throw new IllegalArgumentException("maxMs must be > 0");
            this.maxMs = val;
            return this;
        }

        /**
         * Minimum acceptable success rate 0–100 (default: 99.0).
         */
        public Builder minSuccessPercent(double val) {
            if (val < 0 || val > 100) throw new IllegalArgumentException("minSuccessPercent must be 0–100");
            this.minSuccessPercent = val;
            return this;
        }

        /**
         * Maximum number of failed requests before assertion fails (default: 10).
         */
        public Builder maxFailedRequests(long val) {
            if (val < 0) throw new IllegalArgumentException("maxFailedRequests must be >= 0");
            this.maxFailedRequests = val;
            return this;
        }

        /**
         * Hard simulation timeout in minutes (default: 10).
         */
        public Builder maxDurationMinutes(int val) {
            if (val <= 0) throw new IllegalArgumentException("maxDurationMinutes must be > 0");
            this.maxDurationMinutes = val;
            return this;
        }

        public SlaConfig build() {
            return new SlaConfig(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "SlaConfig{p95=%dms, p99=%dms, mean=%dms, max=%dms, "
                        + "minSuccess=%.1f%%, maxFailed=%d, maxDuration=%dmin}",
                p95Ms, p99Ms, meanMs, maxMs, minSuccessPercent,
                maxFailedRequests, maxDurationMinutes);
    }
}
