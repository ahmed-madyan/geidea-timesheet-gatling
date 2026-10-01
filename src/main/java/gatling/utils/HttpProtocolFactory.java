package gatling.utils;

import gatling.config.enums.ApiHost;
import io.gatling.javaapi.core.Session;
import io.gatling.javaapi.http.HttpDsl;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for constructing and configuring an HttpProtocolBuilder instance
 * for use in Gatling performance simulations.
 */
public class HttpProtocolFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpProtocolFactory.class);

    /**
     * Header used to surface a per-request correlation ID to the system under test.
     * Gatling assigns a fresh UUID to every outgoing HTTP request via this header so
     * that backend logs (and Grafana's "Error details" table, populated by
     * {@code monitoring.InfluxErrorAppender}) can be cross-referenced by ID when a
     * request fails.
     */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /**
     * Gatling SSL settings (global — read at JVM boot from system properties / {@code gatling.conf}).
     * See {@link #acceptAnyCertificate(boolean)}.
     */
    private static final String USE_INSECURE_TRUST_MANAGER = "gatling.ssl.useInsecureTrustManager";
    private static final String ENABLE_HOSTNAME_VERIFICATION = "gatling.http.enableHostnameVerification";

    /**
     * Gatling's HTTP/1.1 default is 6 connections per host (browser-like). That cap
     * applies <em>globally</em> when {@link #withShareConnections()} is used, so API
     * volume tests must raise it to the intended concurrent-VU count or the server
     * only ever sees a handful of sockets.
     */
    public static final int DEFAULT_SHARED_MAX_CONNECTIONS = 200;

    @Getter
    private final String baseUrl;
    private final Map<String, String> headers = new HashMap<>();
    private boolean inferHtmlResources = false;
    private boolean shareConnections = false;
    private int maxConnectionsPerHost = 0;
    private boolean correlationIdEnabled = true;
    private boolean acceptAnyCertificate = false;
    private HttpProtocolBuilder builder;

    /**
     * Constructs the factory with a required base URI.
     *
     * @param baseURI the ApiHost enum used to initialize the base URL
     * @throws IllegalArgumentException if baseURI is null or blank
     */
    public HttpProtocolFactory(ApiHost baseURI) {
        if (baseURI == null || baseURI.toString().isBlank()) {
            LOGGER.error("ApiHost must not be null or blank.");
            throw new IllegalArgumentException("ApiHost must not be null or blank.");
        }

        this.baseUrl = baseURI.toString();
        applyDefaultHeaders();
        LOGGER.info("Initialized HttpProtocolFactory with baseUrl: {}", baseUrl);
    }

    public HttpProtocolFactory(String baseURI) {
        this.baseUrl = baseURI;
        applyDefaultHeaders();
        LOGGER.info("Initialized HttpProtocolFactory with baseUrl String: {}", baseUrl);
    }

    /**
     * Applies the default headers (Accept, Content-Type).
     */
    private void applyDefaultHeaders() {
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        LOGGER.debug("Default headers applied.");
    }

    /**
     * Sets or overrides the Accept header.
     *
     * @param acceptType MIME type to be accepted
     * @return this instance for fluent API
     */
    public HttpProtocolFactory acceptHeader(String acceptType) {
        if (acceptType != null && !acceptType.isBlank()) {
            headers.put("Accept", acceptType);
            LOGGER.debug("Accept header set to: {}", acceptType);
        } else {
            LOGGER.warn("Attempted to set blank or null Accept header. Ignored.");
        }
        return this;
    }

    /**
     * Adds or overrides a specific header.
     *
     * @param key   Header key
     * @param value Header value
     * @return this instance for fluent API
     */
    public HttpProtocolFactory withHeader(String key, String value) {
        if (key != null && value != null) {
            headers.put(key, value);
            LOGGER.debug("Header added: {} = {}", key, value);
        } else {
            LOGGER.warn("Attempted to add null key or value to headers. Skipped.");
        }
        return this;
    }

    /**
     * Adds or overrides multiple headers.
     *
     * @param newHeaders headers to merge
     * @return this instance for fluent API
     */
    public HttpProtocolFactory withHeaders(Map<String, String> newHeaders) {
        if (newHeaders != null) {
            newHeaders.forEach(this::withHeader);
        }
        return this;
    }

    /**
     * Enables automatic fetching of HTML embedded resources (images, scripts,
     * stylesheets, etc.) to simulate real browser behaviour.
     *
     * @return this instance for fluent API
     */
    public HttpProtocolFactory withInferHtmlResources() {
        this.inferHtmlResources = true;
        LOGGER.debug("inferHtmlResources enabled.");
        return this;
    }

    /**
     * Shares one HTTP connection pool across virtual users, sized to
     * {@link #DEFAULT_SHARED_MAX_CONNECTIONS}. Prefer
     * {@link #withShareConnections(int)} and pass the concurrent VU count so the
     * server actually receives that many in-flight requests.
     *
     * @return this instance for fluent API
     */
    public HttpProtocolFactory withShareConnections() {
        return withShareConnections(DEFAULT_SHARED_MAX_CONNECTIONS);
    }

    /**
     * Shares one HTTP connection pool of {@code maxConnectionsPerHost} sockets.
     *
     * <p>Keep this equal to the closed-model concurrent VU count. Gatling's default
     * of 6 (used when sharing without this setter) is why APEX showed almost no
     * volume while the injector reported thousands of virtual users.
     *
     * @param maxConnectionsPerHost pool size / in-flight HTTP/1.1 requests to the host
     * @return this instance for fluent API
     */
    public HttpProtocolFactory withShareConnections(int maxConnectionsPerHost) {
        if (maxConnectionsPerHost <= 0) {
            throw new IllegalArgumentException("maxConnectionsPerHost must be > 0");
        }
        this.shareConnections = true;
        this.maxConnectionsPerHost = maxConnectionsPerHost;
        LOGGER.info("shareConnections enabled with maxConnectionsPerHost={}", maxConnectionsPerHost);
        return this;
    }

    /**
     * Disables the automatic per-request {@value #CORRELATION_ID_HEADER} header.
     *
     * <p>Use this only for endpoints that reject unknown headers (e.g. strict OAuth
     * token endpoints that hash the entire request). When disabled the failed-request
     * row in Grafana's "Error details" table will still be written, just without a
     * correlation-ID column to cross-reference against backend logs.
     *
     * @return this instance for fluent API
     */
    public HttpProtocolFactory withoutCorrelationId() {
        this.correlationIdEnabled = false;
        LOGGER.debug("Per-request {} header disabled.", CORRELATION_ID_HEADER);
        return this;
    }

    /**
     * Disables TLS certificate and hostname validation for HTTPS requests.
     *
     * <p>Gatling 3.x applies SSL trust settings globally (via {@code gatling.conf} or
     * {@code -Dgatling.ssl.useInsecureTrustManager=true}), not per {@link HttpProtocolBuilder}.
     * This method mirrors the legacy {@code .acceptAnyCertificate(true)} DSL: it records the
     * intent on the factory and applies the matching Gatling system properties. Ensure
     * {@code src/test/resources/gatling.conf} keeps {@code gatling.ssl.useInsecureTrustManager}
     * enabled, or pass the property on the JVM command line before Gatling boots.
     *
     * @param accept {@code true} to trust all server certificates (typical for preprod / self-signed)
     * @return this instance for fluent API
     */
    public HttpProtocolFactory acceptAnyCertificate(boolean accept) {
        this.acceptAnyCertificate = accept;
        if (accept) {
            applyInsecureSslSystemProperties();
            LOGGER.warn(
                    "acceptAnyCertificate(true): TLS certificate validation disabled for load testing. "
                            + "Do not use in production-facing runs.");
        } else {
            System.setProperty(USE_INSECURE_TRUST_MANAGER, "false");
            LOGGER.info("acceptAnyCertificate(false): strict TLS trust manager requested via system property.");
        }
        return this;
    }

    /**
     * Equivalent to {@link #acceptAnyCertificate(boolean) acceptAnyCertificate(true)}.
     *
     * @return this instance for fluent API
     */
    public HttpProtocolFactory acceptAnyCertificate() {
        return acceptAnyCertificate(true);
    }

    private static void applyInsecureSslSystemProperties() {
        System.setProperty(USE_INSECURE_TRUST_MANAGER, "true");
        System.setProperty(ENABLE_HOSTNAME_VERIFICATION, "false");
    }

    /**
     * Builds the HttpProtocolBuilder instance with the configured base URL and headers.
     *
     * @return built HttpProtocolBuilder
     */
    public HttpProtocolBuilder build() {
        if (builder != null) {
            LOGGER.warn("Returning previously built HttpProtocolBuilder instance.");
            return builder;
        }

        builder = HttpDsl.http.baseUrl(baseUrl).headers(Collections.unmodifiableMap(headers));
        if (inferHtmlResources) {
            builder = builder.inferHtmlResources();
            LOGGER.info("inferHtmlResources is enabled.");
        }
        if (!shareConnections) {
            shareConnections = true;
            if (maxConnectionsPerHost <= 0) {
                maxConnectionsPerHost = DEFAULT_SHARED_MAX_CONNECTIONS;
            }
            LOGGER.info(
                    "shareConnections enabled by default with maxConnectionsPerHost={} "
                            + "(SimulationFactory resizes this to the injection peak).",
                    maxConnectionsPerHost);
        }
        if (shareConnections) {
            builder = builder.shareConnections();
            LOGGER.info("shareConnections is enabled.");
        }
        if (maxConnectionsPerHost > 0) {
            builder = builder.maxConnectionsPerHost(maxConnectionsPerHost);
            LOGGER.info("maxConnectionsPerHost set to {}.", maxConnectionsPerHost);
        }
        if (acceptAnyCertificate) {
            applyInsecureSslSystemProperties();
            LOGGER.info("HttpProtocolBuilder built with acceptAnyCertificate=true for baseUrl: {}", baseUrl);
        }
        if (correlationIdEnabled) {
            // Prefer an explicit per-request session value so bodies can use the same id via #{correlation}
            // (see KSA paymentGateway Direct chains). If absent or blank, fall back to a fresh UUID per request.
            Function<Session, String> generator = session -> {
                try {
                    if (session.contains("correlation")) {
                        String c = session.getString("correlation");
                        if (c != null && !c.isBlank()) {
                            return c;
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("correlation session lookup failed, using random UUID: {}", e.getMessage());
                }
                return UUID.randomUUID().toString();
            };
            builder = builder.header(CORRELATION_ID_HEADER, generator);
            LOGGER.info("Per-request {} header enabled (overridable via session key {}).",
                    CORRELATION_ID_HEADER, "correlation");
        }
        LOGGER.info("HttpProtocolBuilder built with baseUrl: {}", baseUrl);
        return builder;
    }

    /**
     * Retrieves an unmodifiable view of all configured headers.
     *
     * @return map of headers
     */
    public Map<String, String> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }
}