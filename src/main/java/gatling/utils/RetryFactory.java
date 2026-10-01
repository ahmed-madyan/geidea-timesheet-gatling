package gatling.utils;

import io.gatling.javaapi.core.ChainBuilder;

import static io.gatling.javaapi.core.CoreDsl.doIf;
import static io.gatling.javaapi.core.CoreDsl.exec;
import static io.gatling.javaapi.core.CoreDsl.pause;
import static io.gatling.javaapi.core.CoreDsl.tryMax;

/**
 * Shared {@code tryMax} + pause wrappers for endpoint chains.
 *
 * <p>Defaults are driven by JVM system properties (see README):
 * <ul>
 *   <li>{@code -Dretries} — attempts (default {@code 10})</li>
 *   <li>{@code -DretryPauseSeconds} — delay between <em>retries</em> in seconds (default {@code 2}).
 *       The pause does not run after a successful request, and not before the first attempt.
 *       Pass {@code 0} (or {@code -DretryPauseSeconds=0}) to disable it.</li>
 * </ul>
 *
 * <pre>{@code
 * return exec(
 *     RetryFactory.withRetry("getCart", getCart()),
 *     RetryFactory.withRetry("resolveTargetItemIdStep", resolveTargetItemIdStep())
 * );
 * }</pre>
 */
public final class RetryFactory {

    /** Max attempts from {@code -Dretries} (default 10). */
    public static final int RETRIES = Integer.parseInt(System.getProperty("retries", "10"));

    /** Pause between retries from {@code -DretryPauseSeconds} (default 2). */
    public static final int RETRY_PAUSE_SECONDS =
            Integer.parseInt(System.getProperty("retryPauseSeconds", "2"));

    private RetryFactory() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Retries {@code chain} with project defaults, then exits the virtual user on failure.
     */
    public static ChainBuilder withRetry(String name, ChainBuilder chain) {
        return withRetry(name, chain, RETRIES, RETRY_PAUSE_SECONDS);
    }

    /**
     * Retries {@code chain} with project retry count and a custom pause, then exits on failure.
     */
    public static ChainBuilder withRetry(String name, ChainBuilder chain, int pauseSeconds) {
        return withRetry(name, chain, RETRIES, pauseSeconds);
    }

    /**
     * Retries {@code chain} with an explicit attempt count and pause, then exits on failure.
     */
    public static ChainBuilder withRetry(String name, ChainBuilder chain, int retries, int pauseSeconds) {
        return tryMax(retries, name).on(retryBody(name, chain, pauseSeconds)).exitHereIfFailed();
    }

    /**
     * Like {@link #withRetry(String, ChainBuilder, int, int)} but does not call
     * {@code exitHereIfFailed}, so the scenario can continue after exhausted retries.
     */
    public static ChainBuilder withRetryContinue(String name, ChainBuilder chain, int retries, int pauseSeconds) {
        return tryMax(retries, name).on(retryBody(name, chain, pauseSeconds));
    }

    /**
     * {@code tryMax} counter {@code name} starts at 0. Pause only when that counter is already
     * {@code > 0} (i.e. this is a retry), so successful iterations are not paced at 2 s.
     */
    private static ChainBuilder retryBody(String name, ChainBuilder chain, int pauseSeconds) {
        if (pauseSeconds <= 0) {
            return chain;
        }
        return exec(
                doIf(session -> session.getInt(name) > 0).then(pause(pauseSeconds)),
                chain);
    }
}