package gatling.utils;

import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.ClosedInjectionStep;
import io.gatling.javaapi.core.OpenInjectionStep;
import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Session;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.gatling.javaapi.core.CoreDsl.*;

/**
 * Provides factory methods to create various user load injection profiles
 * for both <em>open</em> and <em>closed</em> Gatling simulation models.
 *
 * <h3>Open model (arrival rate)</h3>
 * Users are injected at a defined rate regardless of active-user count.
 * Use with {@link gatling.utils.SimulationFactory#injectOpen}.
 * <ul>
 *   <li>{@link #spike}</li>
 *   <li>{@link #rampUp}</li>
 *   <li>{@link #steadyUsers}</li>
 *   <li>{@link #stressRamp}</li>
 *   <li>{@link #incrementingLoad}</li>
 * </ul>
 *
 * <h3>Closed model (concurrent users)</h3>
 * The system maintains a fixed number of concurrent virtual users at all times.
 * Use with {@link gatling.utils.SimulationFactory#injectClosed}.
 * <ul>
 *   <li>{@link #concurrentUsers}</li>
 *   <li>{@link #rampConcurrentUsers}</li>
 *   <li>{@link #rampDownConcurrentUsers}</li>
 *   <li>{@link #concurrentUntilSuccess}</li>
 * </ul>
 *
 * <h3>Success-count runs</h3>
 * Prefer {@link #concurrentUntilSuccess(int, int)} — keep {@code threads} concurrent VUs
 * until {@code targetedSuccess} successful scenario iterations complete (not a fixed duration).
 *
 * <h3>Connection pool</h3>
 * Each factory method records its peak VU count. {@link SimulationFactory} then applies
 * {@code shareConnections} + {@code maxConnectionsPerHost(peak)} so the server actually
 * receives that many in-flight HTTP/1.1 connections (Gatling's default cap is 6).
 */
public final class LoadProfileFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadProfileFactory.class);

    /**
     * Peak concurrent VUs (or arrival-rate hint) recorded while building the current
     * injection. {@link SimulationFactory} consumes this via {@link #takePeakConcurrentUsers()}.
     */
    private static final AtomicInteger PEAK_CONCURRENT_USERS = new AtomicInteger(0);

    private LoadProfileFactory() {
        throw new UnsupportedOperationException("LoadProfileFactory is a utility class and cannot be instantiated.");
    }

    static void rememberPeak(int users) {
        if (users > 0) {
            PEAK_CONCURRENT_USERS.updateAndGet(current -> Math.max(current, users));
        }
    }

    /**
     * Returns the peak recorded since the last take and resets the counter to 0.
     */
    static int takePeakConcurrentUsers() {
        return PEAK_CONCURRENT_USERS.getAndSet(0);
    }

    // =========================================================================
    // Open Model Profiles
    // =========================================================================

    /**
     * Creates a spike load injection with all users injected at once.
     *
     * @param users the number of users to inject
     * @return an OpenInjectionStep configured for spike
     * @throws IllegalArgumentException if users &le; 0
     */
    public static OpenInjectionStep spike(int users) {
        validate(users, "SPIKE");
        rememberPeak(users);
        LOGGER.info("Creating SPIKE profile with {} users injected immediately.", users);
        return atOnceUsers(users);
    }

    /**
     * Creates a ramp-up load injection over time.
     *
     * @param users           the total number of users to inject
     * @param durationSeconds duration in seconds for ramp-up
     * @return configured OpenInjectionStep
     * @throws IllegalArgumentException if users &le; 0 or duration &le; 0
     */
    public static OpenInjectionStep rampUp(int users, int durationSeconds) {
        validate(users, durationSeconds, "RAMP-UP");
        rememberPeak(users);
        LOGGER.info("Creating RAMP-UP profile with {} users over {} seconds.", users, durationSeconds);
        return rampUsers(users).during(Duration.ofSeconds(durationSeconds));
    }

    /**
     * Creates a steady load injection with a constant arrival rate.
     *
     * @param usersPerSec     number of new users per second
     * @param durationSeconds duration of the steady load
     * @return configured OpenInjectionStep
     * @throws IllegalArgumentException if usersPerSec &le; 0 or duration &le; 0
     */
    public static OpenInjectionStep steadyUsers(int usersPerSec, int durationSeconds) {
        validate(usersPerSec, durationSeconds, "STEADY");
        rememberPeak(usersPerSec);
        LOGGER.info("Creating STEADY profile with {} users/sec for {} seconds.", usersPerSec, durationSeconds);
        return constantUsersPerSec(usersPerSec).during(Duration.ofSeconds(durationSeconds));
    }

    /**
     * Creates a stress ramp load injection, gradually increasing the arrival rate.
     *
     * @param fromUsers       starting arrival rate (users/sec)
     * @param toUsers         ending arrival rate (users/sec), must be &gt; fromUsers
     * @param durationSeconds duration over which to ramp up
     * @return configured OpenInjectionStep
     * @throws IllegalArgumentException for invalid user ranges or duration
     */
    public static OpenInjectionStep stressRamp(int fromUsers, int toUsers, int durationSeconds) {
        if (fromUsers < 0 || toUsers <= fromUsers || durationSeconds <= 0) {
            String msg = String.format("Invalid STRESS RAMP: from=%d, to=%d, duration=%d",
                    fromUsers, toUsers, durationSeconds);
            LOGGER.error("{}", msg);
            throw new IllegalArgumentException(msg);
        }
        rememberPeak(toUsers);
        LOGGER.info("Creating STRESS RAMP from {} to {} users/sec over {} seconds.", fromUsers, toUsers, durationSeconds);
        return rampUsersPerSec(fromUsers).to(toUsers).during(Duration.ofSeconds(durationSeconds));
    }

    /**
     * Creates a staircase (incremental) load profile using Gatling's built-in
     * {@code incrementUsersPerSec} DSL.
     *
     * <p>The load climbs in discrete steps:
     * <pre>
     *   startRps → startRps+increment → startRps+2*increment → … (levels times)
     * </pre>
     * Each level is held for {@code levelDurationSec} seconds, with a linear ramp
     * of {@code rampSec} seconds between levels.
     *
     * <p>Example — ramp from 10 to 60 RPS in 5 steps of 10 RPS, each step 60 s:
     * <pre>{@code
     * LoadProfileFactory.incrementingLoad(10, 10, 5, 60, 10)
     * }</pre>
     *
     * @param startRps         starting users/sec (must be &ge; 0)
     * @param rpsIncrement     users/sec increment per step (must be &gt; 0)
     * @param levels           number of steps to execute (must be &gt; 0)
     * @param levelDurationSec seconds to hold each level (must be &gt; 0)
     * @param rampSec          seconds for the ramp between levels (must be &ge; 0)
     * @return configured OpenInjectionStep
     */
    public static OpenInjectionStep incrementingLoad(
            int startRps, int rpsIncrement, int levels,
            int levelDurationSec, int rampSec) {

        if (startRps < 0 || rpsIncrement <= 0 || levels <= 0
                || levelDurationSec <= 0 || rampSec < 0) {
            String msg = String.format(
                    "Invalid INCREMENTING LOAD: startRps=%d, increment=%d, levels=%d, levelDuration=%d, ramp=%d",
                    startRps, rpsIncrement, levels, levelDurationSec, rampSec);
            LOGGER.error("{}", msg);
            throw new IllegalArgumentException(msg);
        }

        rememberPeak(startRps + rpsIncrement * levels);
        LOGGER.info("Creating INCREMENTING LOAD: startRps={}, +{}/step, {} levels, {}s/level, {}s ramp.",
                startRps, rpsIncrement, levels, levelDurationSec, rampSec);

        return incrementUsersPerSec(rpsIncrement)
                .times(levels)
                .eachLevelLasting(Duration.ofSeconds(levelDurationSec))
                .separatedByRampsLasting(Duration.ofSeconds(rampSec))
                .startingFrom(startRps);
    }

    // =========================================================================
    // Closed Model Profiles
    // =========================================================================

    /**
     * Creates a closed-model injection that maintains a constant number of
     * concurrent virtual users throughout the duration.
     *
     * <p>Use with {@link gatling.utils.SimulationFactory#injectClosed}.
     *
     * @param users           number of concurrent users to maintain (must be &gt; 0)
     * @param durationSeconds duration in seconds (must be &gt; 0)
     * @return configured ClosedInjectionStep
     * @throws IllegalArgumentException if users &le; 0 or duration &le; 0
     */
    public static ClosedInjectionStep concurrentUsers(int users, int durationSeconds) {
        validate(users, durationSeconds, "CONCURRENT USERS");
        rememberPeak(users);
        LOGGER.info("Creating CONCURRENT USERS profile: {} users for {} seconds.", users, durationSeconds);
        return constantConcurrentUsers(users).during(Duration.ofSeconds(durationSeconds));
    }

    /**
     * Keeps {@code threads} virtual users running until
     * {@code targetedSuccess} successful scenario iterations complete.
     *
     * <p>Uses a closed injector so {@code threads} VUs stay in flight (failed users are
     * replaced) and the shared connection pool is sized to {@code threads}. The run stops
     * when the success target is hit, not after a clock duration.
     *
     * <pre>{@code
     * ScenarioBuilder scn = MyScenario.buildScenario();
     *
     * new SimulationFactory(scn, protocol)
     *         .injectClosed(LoadProfileFactory.concurrentUntilSuccess(10, 500))
     *         .build();
     *
     * // or
     * new SimulationFactory(scn, protocol)
     *         .injectOpen(LoadProfileFactory.concurrentUntilSuccess(10, 500))
     *         .build();
     * }</pre>
     *
     * @param threads         number of concurrent VUs to keep in flight (must be &gt; 0)
     * @param targetedSuccess number of successful iterations to complete (must be &gt; 0)
     * @return profile that wraps the scenario and injects {@code threads} VUs until the target
     */
    public static ConcurrentUntilSuccess concurrentUntilSuccess(int threads, int targetedSuccess) {
        if (threads <= 0 || targetedSuccess <= 0) {
            String msg = String.format(
                    "Invalid CONCURRENT UNTIL SUCCESS: threads=%d, targetedSuccess=%d",
                    threads, targetedSuccess);
            LOGGER.error("{}", msg);
            throw new IllegalArgumentException(msg);
        }
        rememberPeak(threads);
        LOGGER.info(
                "Creating CONCURRENT UNTIL SUCCESS: {} concurrent VUs until {} successes.",
                threads, targetedSuccess);
        return new ConcurrentUntilSuccess(threads, targetedSuccess);
    }

    /**
     * Creates a closed-model injection that linearly ramps the number of concurrent
     * users from {@code fromUsers} to {@code toUsers} over the given duration.
     *
     * <p>Use with {@link gatling.utils.SimulationFactory#injectClosed}.
     *
     * @param fromUsers       starting number of concurrent users (&ge; 0)
     * @param toUsers         target number of concurrent users (&gt; fromUsers)
     * @param durationSeconds duration of the ramp (must be &gt; 0)
     * @return configured ClosedInjectionStep
     * @throws IllegalArgumentException for invalid ranges or duration
     */
    public static ClosedInjectionStep rampConcurrentUsers(int fromUsers, int toUsers, int durationSeconds) {
        if (fromUsers < 0 || toUsers <= fromUsers || durationSeconds <= 0) {
            String msg = String.format(
                    "Invalid RAMP CONCURRENT USERS: from=%d, to=%d, duration=%d",
                    fromUsers, toUsers, durationSeconds);
            LOGGER.error("{}", msg);
            throw new IllegalArgumentException(msg);
        }
        rememberPeak(toUsers);
        LOGGER.info("Creating RAMP CONCURRENT USERS from {} to {} over {} seconds.",
                fromUsers, toUsers, durationSeconds);
        return io.gatling.javaapi.core.CoreDsl.rampConcurrentUsers(fromUsers)
                .to(toUsers)
                .during(Duration.ofSeconds(durationSeconds));
    }

    /**
     * Creates a closed-model injection that linearly ramps the number of concurrent
     * users down from {@code fromUsers} to {@code toUsers} over the given duration.
     *
     * <p>Use with {@link gatling.utils.SimulationFactory#injectClosed}.
     *
     * @param fromUsers       starting number of concurrent users (must be &gt; 0)
     * @param toUsers         target number of concurrent users (&ge; 0, must be &lt; fromUsers)
     * @param durationSeconds duration of the ramp (must be &gt; 0)
     * @return configured ClosedInjectionStep
     * @throws IllegalArgumentException for invalid ranges or duration
     */
    public static ClosedInjectionStep rampDownConcurrentUsers(
            int fromUsers, int toUsers, int durationSeconds) {
        if (fromUsers <= 0 || toUsers < 0 || toUsers >= fromUsers || durationSeconds <= 0) {
            String msg = String.format(
                    "Invalid RAMP DOWN CONCURRENT USERS: from=%d, to=%d, duration=%d",
                    fromUsers, toUsers, durationSeconds);
            LOGGER.error("{}", msg);
            throw new IllegalArgumentException(msg);
        }
        rememberPeak(fromUsers);
        LOGGER.info("Creating RAMP DOWN CONCURRENT USERS from {} to {} over {} seconds.",
                fromUsers, toUsers, durationSeconds);
        return io.gatling.javaapi.core.CoreDsl.rampConcurrentUsers(fromUsers)
                .to(toUsers)
                .during(Duration.ofSeconds(durationSeconds));
    }

    // =========================================================================
    // Concurrent-until-success profile
    // =========================================================================

    /**
     * Profile: {@code threads} VUs until {@code targetedSuccess} successful scenario iterations.
     *
     * <p>Pass to {@link SimulationFactory#injectOpen(ConcurrentUntilSuccess)} or
     * {@link SimulationFactory#injectClosed(ConcurrentUntilSuccess)} — the factory keeps your
     * existing {@code buildScenario()} usage and this profile wraps it automatically.
     */
    public static final class ConcurrentUntilSuccess {

        /**
         * Gatling's closed injector requires {@code during(duration)}. This is not the stop
         * condition — {@code stopInjector} fires at the success target. Failed VUs are
         * replaced for the whole ceiling so the server keeps seeing {@code threads} sockets.
         */
        private static final Duration INJECTOR_CEILING = Duration.ofDays(365);

        private final int threads;
        private final int targetedSuccess;
        private final AtomicInteger successes = new AtomicInteger();

        private ConcurrentUntilSuccess(int threads, int targetedSuccess) {
            this.threads = threads;
            this.targetedSuccess = targetedSuccess;
        }

        /** @return thread / VU count */
        public int threads() {
            return threads;
        }

        /** @return targeted successful completions */
        public int targetedSuccess() {
            return targetedSuccess;
        }

        /** @return successes recorded so far */
        public int successCount() {
            return successes.get();
        }

        /**
         * Closed injector: hold {@link #threads()} in-flight VUs until the success target.
         *
         * <p>Gatling requires a {@code during(...)} window on closed injection. The window is
         * a ceiling only — {@code stopInjector} ends the run when {@link #targetedSuccess()}
         * is reached. Failed VUs are replaced so the server keeps seeing {@code threads}
         * connections.
         */
        public ClosedInjectionStep closedInjection() {
            rememberPeak(threads);
            return constantConcurrentUsers(threads).during(INJECTOR_CEILING);
        }

        /**
         * Inject {@link #threads()} users at once. Prefer {@link #closedInjection()} so
         * finished/failed VUs are replaced and the server keeps receiving the volume.
         */
        public OpenInjectionStep openInjection() {
            rememberPeak(threads);
            return atOnceUsers(threads);
        }

        /**
         * Wraps an existing {@code buildScenario()} result in an {@code asLongAs} loop that
         * stops after {@link #targetedSuccess()} successful completions.
         *
         * <p>Called automatically by {@link SimulationFactory#injectOpen(ConcurrentUntilSuccess)}
         * / {@link SimulationFactory#injectClosed(ConcurrentUntilSuccess)}.
         */
        public ScenarioBuilder wrap(ScenarioBuilder original) {
            if (original == null) {
                throw new IllegalArgumentException("scenario is required");
            }

            String name = original.wrapped.name();
            ChainBuilder body = chainOf(original);

            return io.gatling.javaapi.core.CoreDsl.scenario(name)
                    .asLongAs(this::belowTarget, true)
                    .on(exec(body)
                            .exec(this::recordSuccess)
                            .exec(stopInjectorIf(
                                    session -> String.format(
                                            "Reached %d successful completions (count=%d)",
                                            targetedSuccess, successes.get()),
                                    session -> successes.get() >= targetedSuccess)));
        }

        /**
         * Convert a Java {@link ScenarioBuilder} into a {@link ChainBuilder} via Gatling's
         * Scala {@code toChainBuilder()}, so the original scenario actions can be looped.
         */
        private static ChainBuilder chainOf(ScenarioBuilder scenario) {
            try {
                var ctor = ChainBuilder.class.getDeclaredConstructor(
                        io.gatling.core.structure.ChainBuilder.class);
                ctor.setAccessible(true);
                return ctor.newInstance(scenario.wrapped.toChainBuilder());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "Unable to wrap ScenarioBuilder for concurrentUntilSuccess", e);
            }
        }

        private boolean belowTarget(Session session) {
            return successes.get() < targetedSuccess;
        }

        private Session recordSuccess(Session session) {
            if (session.isFailed()) {
                return session;
            }
            int n = successes.incrementAndGet();
            if (n == 1 || n == targetedSuccess || n % 50 == 0) {
                LOGGER.info("Successful completions: {}/{}", n, targetedSuccess);
            }
            return session;
        }
    }

    // =========================================================================
    // Private Validators
    // =========================================================================

    private static void validate(int users, String profile) {
        if (users <= 0) {
            String msg = String.format("Invalid %s profile: users=%d", profile.toUpperCase(), users);
            LOGGER.error("{}", msg);
            throw new IllegalArgumentException(msg);
        }
    }

    private static void validate(int users, int duration, String profile) {
        if (users <= 0 || duration <= 0) {
            String msg = String.format("Invalid %s profile: users=%d, duration=%d",
                    profile.toUpperCase(), users, duration);
            LOGGER.error("{}", msg);
            throw new IllegalArgumentException(msg);
        }
    }
}
