package gatling.utils;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-wide unique merchant ID generator for APEX create-merchant chains.
 *
 * <p>Unlike per-class {@code ThreadLocalRandom} + local {@code Set} approaches, this class
 * keeps a <strong>single JVM-wide counter and registry</strong>, so concurrent virtual users
 * across any create-merchant endpoint never reuse the same {@code parentBusinessId} /
 * {@code subMerchantId}.
 *
 * <p>Cross-run collisions are minimized by seeding the counter from wall-clock millis XOR
 * nanoTime (override with {@code -DcreateMerchantBusinessIdSeed=NNNNNNNN}).
 */
public final class MerchantIdGenerator {

    private static final long BUSINESS_ID_MIN_INCLUSIVE = 10_000_000L;
    private static final long BUSINESS_ID_MAX_EXCLUSIVE = 100_000_000L;
    private static final long BUSINESS_ID_RANGE =
            BUSINESS_ID_MAX_EXCLUSIVE - BUSINESS_ID_MIN_INCLUSIVE;

    private static final AtomicLong BUSINESS_ID_COUNTER = new AtomicLong(initialBusinessIdSeed());
    private static final AtomicInteger SUB_SEQUENCE_COUNTER = new AtomicInteger(0);

    private static final Set<String> GENERATED_BUSINESS_IDS = ConcurrentHashMap.newKeySet();
    private static final Set<String> GENERATED_SUB_MERCHANT_IDS = ConcurrentHashMap.newKeySet();

    private MerchantIdGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns the next unique 8-digit {@code parentBusinessId} (zero-padded).
     * Guaranteed unique within this JVM; seed is time-based across JVM starts unless
     * {@code -DcreateMerchantBusinessIdSeed} is set.
     */
    public static String nextBusinessId() {
        String candidate;
        do {
            long raw = BUSINESS_ID_COUNTER.getAndIncrement();
            long value = BUSINESS_ID_MIN_INCLUSIVE + Math.floorMod(raw, BUSINESS_ID_RANGE);
            candidate = String.format("%08d", value);
        } while (!GENERATED_BUSINESS_IDS.add(candidate));
        return candidate;
    }

    /**
     * Returns a unique {@code subMerchantId} for {@code businessId} as
     * {@code businessId + NNN} (3-digit suffix). Suffixes are allocated from a process-wide
     * counter and tracked in a shared set, so they stay unique even when multiple chains
     * run in the same JVM.
     */
    public static String nextSubMerchantId(String businessId) {
        if (businessId == null || businessId.isBlank()) {
            throw new IllegalArgumentException("businessId must not be blank");
        }
        String candidate;
        do {
            int suffix = Math.floorMod(SUB_SEQUENCE_COUNTER.getAndIncrement(), 1_000);
            candidate = businessId + String.format("%03d", suffix);
        } while (!GENERATED_SUB_MERCHANT_IDS.add(candidate));
        return candidate;
    }

    private static long initialBusinessIdSeed() {
        String override = System.getProperty("createMerchantBusinessIdSeed");
        if (override != null && !override.isBlank()) {
            long seed = Long.parseLong(override.trim());
            if (seed < BUSINESS_ID_MIN_INCLUSIVE || seed >= BUSINESS_ID_MAX_EXCLUSIVE) {
                throw new IllegalArgumentException(
                        "createMerchantBusinessIdSeed must be in ["
                                + BUSINESS_ID_MIN_INCLUSIVE + ", "
                                + BUSINESS_ID_MAX_EXCLUSIVE + ")");
            }
            return seed;
        }
        long mixed = System.currentTimeMillis() ^ (System.nanoTime() >>> 8);
        return BUSINESS_ID_MIN_INCLUSIVE + Math.floorMod(mixed, BUSINESS_ID_RANGE);
    }
}
