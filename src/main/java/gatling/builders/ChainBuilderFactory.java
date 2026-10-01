package gatling.builders;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gatling.config.env.EnvConfig;
import gatling.config.region.RegionConfig;
import gatling.config.enums.ApiPath;
import gatling.config.enums.HttpMethod;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.CheckBuilder;
import io.gatling.javaapi.http.BodyPart;
import io.gatling.javaapi.http.HttpRequestActionBuilder;
import io.gatling.javaapi.core.FeederBuilder;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import gatling.config.enums.StatusCode;

import static io.gatling.javaapi.core.CoreDsl.*;
import static io.gatling.javaapi.http.HttpDsl.*;

/**
 * Factory class for building Gatling chains with a fluent API.
 * Provides methods to create HTTP requests with various HTTP methods,
 * headers, body, and checks.
 */
public class ChainBuilderFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChainBuilderFactory.class);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Getter
    private final String name;
    private HttpMethod method;
    private String path;
    private String body;
    private String bodyFile;
    private java.util.function.Function<io.gatling.javaapi.core.Session, String> dynamicBody;
    private final List<CheckBuilder> checks = new ArrayList<>();
    private final Map<String, String> headers = new java.util.HashMap<>();
    private final Map<String, java.util.function.Function<io.gatling.javaapi.core.Session, String>> dynamicHeaders =
            new java.util.LinkedHashMap<>();
    private Map<String, Object> formParams = new java.util.HashMap<>();
    private final List<BodyPart> multipartParts = new ArrayList<>();
    /**
     * When {@code true} (via {@link #asMultipartForm()}), form fields alone are encoded as multipart.
     * Otherwise multipart is still used whenever any multipart part is configured.
     */
    private boolean useMultipartFormEncoding;
    private boolean disableFollowRedirect = false;
    private FeederBuilder<?> feeder;

    /**
     * Creates a new ChainBuilderFactory with the given request name.
     *
     * @param name The name of the request
     */
    public ChainBuilderFactory(String name) {
        this.name = name;
        LOGGER.debug("Created new ChainBuilderFactory with name: {}", name);
    }

    /**
     * Sets the HTTP method and path for the request.
     *
     * @param method The HTTP method enum
     * @param path   The request path
     * @return this instance for fluent API
     */
    public ChainBuilderFactory request(HttpMethod method, String path) {
        LOGGER.debug("Setting request method: {} and path: {} for chain: {}", method, path, name);
        this.method = method;
        this.path = path;
        return this;
    }

    /**
     * Sets the request body.
     *
     * @param body The request body as a JSON string
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withBody(String body) {
        LOGGER.debug("Setting request body for chain: {}. Body: {}", name, body);
        this.body = body;
        return this;
    }

    /**
     * Sets the request body from a file path.
     *
     * @param filePath The path to the JSON file relative to resources/bodies
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withBodyFromFile(String filePath) {
        LOGGER.debug("Setting request body from file for chain: {}. File: {}", name, filePath);
        this.bodyFile = filePath;
        return this;
    }

    /**
     * Sets the request body from a session function (dynamically evaluated).
     *
     * @param dynamicBody The logic to construct the request body at runtime
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withDynamicBody(java.util.function.Function<io.gatling.javaapi.core.Session, String> dynamicBody) {
        LOGGER.debug("Setting dynamic request body for chain: {}", name);
        this.dynamicBody = dynamicBody;
        return this;
    }

    /**
     * Adds a form parameter to the request.
     *
     * @param key   The form parameter name
     * @param value The form parameter value
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withFormParam(String key, Object value) {
        LOGGER.debug("Adding form param {}: {} for chain: {}", key, value, name);
        this.formParams.put(key, value);
        return this;
    }

    /**
     * Adds a multipart/form-data file part loaded from classpath {@code bodies/&lt;filePath&gt;}
     * (same layout as {@link #withBodyFromFile(String)}).
     *
     * <p>Multipart requests use Gatling {@code bodyParts(..).}{@link io.gatling.javaapi.http.RequestWithBodyActionBuilder#asMultipartForm() asMultipartForm()}.
     * Call {@link #asMultipartForm()} for form-only multipart. With file or text multipart parts configured,
     * {@link #withFormParam(String, Object)} entries are sent as multipart text parts before binary parts.</p>
     *
     * <p>Mutually exclusive with {@link #withBody(String)}, {@link #withBodyFromFile(String)},
     * and {@link #withDynamicBody(java.util.function.Function)}.</p>
     *
     * @param fieldName          HTML form field name for the file part ({@code Content-Disposition})
     * @param bodiesRelativePath path under {@code src/test/resources/bodies/}
     */
    public ChainBuilderFactory withMultipartFileAttachment(String fieldName, String bodiesRelativePath) {
        LOGGER.debug("Adding multipart file part '{}' from bodies/{} for chain: {}", fieldName, bodiesRelativePath, name);
        multipartParts.add(RawFileBodyPart(fieldName, "bodies/" + bodiesRelativePath));
        return this;
    }

    /**
     * Same as {@link #withMultipartFileAttachment(String, String)}, with explicit client filename and part Content-Type.
     */
    public ChainBuilderFactory withMultipartFileAttachment(
            String fieldName, String bodiesRelativePath, String uploadedFileName, String contentType) {
        LOGGER.debug(
                "Adding multipart file part '{}' from bodies/{}, fileName={}, contentType={}, chain: {}",
                fieldName,
                bodiesRelativePath,
                uploadedFileName,
                contentType,
                name);
        BodyPart part = RawFileBodyPart(fieldName, "bodies/" + bodiesRelativePath);
        if (uploadedFileName != null && !uploadedFileName.isEmpty()) {
            part = part.fileName(uploadedFileName);
        }
        if (contentType != null && !contentType.isEmpty()) {
            part = part.contentType(contentType);
        }
        multipartParts.add(part);
        return this;
    }

    /**
     * Adds a plain UTF-8 text field to {@code multipart/form-data} ({@code StringBodyPart}).
     * Use when you need extra parts beyond {@link #withFormParam(String, Object)}.
     */
    public ChainBuilderFactory withMultipartTextPart(String fieldName, String value) {
        LOGGER.debug("Adding multipart text part '{}' for chain: {}", fieldName, name);
        multipartParts.add(StringBodyPart(fieldName, value));
        return this;
    }

    /**
     * Low-level escape hatch for custom Gatling body parts ({@code ElFileBodyPart}, {@code ByteArrayBodyPart}, etc.).
     */
    public ChainBuilderFactory withMultipartBodyPart(BodyPart part) {
        if (part != null) {
            LOGGER.debug("Adding custom multipart BodyPart for chain: {}", name);
            multipartParts.add(part);
        } else {
            LOGGER.warn("Ignoring null BodyPart for chain: {}", name);
        }
        return this;
    }

    /**
     * Declare that this request must be encoded as HTTP {@code multipart/form-data}.
     *
     * <ul>
     *   <li>Optional after multipart file/text parts — documents intent;</li>
     *   <li>Required for form-field-only payloads: combine with {@link #withFormParam} so Gatling emits
     *       multipart parts instead of URL-encoded ({@code application/x-www-form-urlencoded}) form.</li>
     * </ul>
     *
     * <p>Build always invokes Gatling {@code bodyParts(...).}{@link io.gatling.javaapi.http.RequestWithBodyActionBuilder#asMultipartForm() asMultipartForm()}.</p>
     *
     * @return this instance for fluent API
     */
    public ChainBuilderFactory asMultipartForm() {
        LOGGER.debug("Enabling multipart/form-data encoding for chain: {}", name);
        this.useMultipartFormEncoding = true;
        return this;
    }

    /**
     * Disables following redirects for this request.
     *
     * @return this instance for fluent API
     */
    public ChainBuilderFactory disableFollowRedirect() {
        LOGGER.debug("Disabling follow redirect for chain: {}", name);
        this.disableFollowRedirect = true;
        return this;
    }

    /**
     * Adds a header to the request.
     *
     * @param key   The header name
     * @param value The header value
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withHeader(String key, String value) {
        LOGGER.debug("Adding header {}: {} for chain: {}", key, value, name);
        this.headers.put(key, value);
        return this;
    }

    /**
     * Adds an Authorization header with a Bearer token.
     *
     * @param tokenExpression The token or Gatling EL string (e.g. "#{token}")
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withBearerToken(String tokenExpression) {
        LOGGER.debug("Adding Bearer token authorization for chain: {}", name);
        return withHeader("Authorization", "Bearer " + tokenExpression);
    }

    /**
     * Adds an Authorization header with a Basic token.
     *
     * @param tokenExpression The Base64 credentials or Gatling EL string
     *                        (e.g. "#{basicToken}" or a pre-encoded value). Do not include the
     *                        {@code Basic } prefix — it is added automatically.
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withBasicToken(String tokenExpression) {
        LOGGER.debug("Adding Basic token authorization for chain: {}", name);
        return withHeader("Authorization", "Basic " + tokenExpression);
    }

    /**
     * Adds multiple headers to the request.
     *
     * @param headers Map of header names to values
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withHeaders(Map<String, String> headers) {
        LOGGER.debug("Adding multiple headers for chain: {}. Headers: {}", name, headers);
        this.headers.putAll(headers);
        return this;
    }

    /**
     * Adds a header whose value is computed from the {@link io.gatling.javaapi.core.Session} on each request
     * (e.g. per-feeder Basic auth).
     */
    public ChainBuilderFactory withDynamicHeader(
            String key, java.util.function.Function<io.gatling.javaapi.core.Session, String> valueSupplier) {
        if (key != null && valueSupplier != null) {
            LOGGER.debug("Adding dynamic header '{}' for chain: {}", key, name);
            dynamicHeaders.put(key, valueSupplier);
        } else {
            LOGGER.warn("Skipping dynamic header with null key or supplier for chain: {}", name);
        }
        return this;
    }

    /**
     * Adds the standard set of browser/curl-style headers commonly required by the
     * portal and admin APIs (e.g. {@code Sec-Fetch-*}, {@code Accept-Language},
     * {@code Accept-Encoding}, {@code X-ApplicationLanguage}, {@code X-CounterpartyCode},
     * {@code Priority}, {@code acceptLanguage}).
     *
     * <p>The {@code Origin} and {@code Referer} headers are caller-provided since they
     * differ per endpoint. Region-specific values ({@code X-CounterpartyCode} and
     * {@code X-ApplicationLanguage}) are sourced from the active {@link RegionConfig}.
     *
     * <p>Existing headers are preserved unless they share a key with one set here, in
     * which case the latest value wins (consistent with {@link #withHeader(String, String)}).
     *
     * @param origin  value for the {@code Origin} header
     * @param referer value for the {@code Referer} header
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withBrowserHeaders(String origin, String referer) {
        LOGGER.debug("Adding standard browser headers for chain: {} (origin={}, referer={})", name, origin, referer);
        RegionConfig region = RegionConfig.active();
        return withHeader("Origin", origin)
                .withHeader("Referer", referer)
                .withHeader("Sec-Fetch-Site", "same-site")
                .withHeader("Sec-Fetch-Mode", "cors")
                .withHeader("Sec-Fetch-Dest", "empty")
                .withHeader("Accept-Language", "en-GB,en-US;q=0.9,en;q=0.8")
                .withHeader("Accept-Encoding", "gzip, deflate, br, zstd")
                .withHeader("X-ApplicationLanguage", region.defaultAppLanguage())
                .withHeader("X-CounterpartyCode", region.defaultCounterparty())
                .withHeader("Priority", "u=3, i")
                .withHeader("Content-Type", "application/json")
                .withHeader("Accept", "text/plain")
                .withHeader("acceptLanguage", "en");
    }

    /**
     * Convenience overload of {@link #withBrowserHeaders(String, String)} that uses the
     * same value for both {@code Origin} and {@code Referer}.
     *
     * @param origin value for both {@code Origin} and {@code Referer}
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withBrowserHeaders(String origin) {
        return withBrowserHeaders(origin, origin);
    }

    /**
     * Convenience overload of {@link #withBrowserHeaders(String, String)} that defaults
     * both {@code Origin} and {@code Referer} to the active environment's base URI
     * (see {@link EnvConfig#active()}).
     *
     * <p>Use this for endpoints whose {@code Origin}/{@code Referer} match the API host
     * itself (e.g. the contract / onboarding flows). For endpoints that originate from a
     * different host (portal, onboarding portal, www, ...), use the parameterized
     * overloads instead.
     *
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withBrowserHeaders() {
        return withBrowserHeaders(EnvConfig.active().baseUri().portal().toString());
    }

    /**
     * Adds the region-aware "API client" header triple commonly used by the merchant
     * portal and analytics APIs that do not require browser-style {@code Origin}/{@code Referer}:
     * <ul>
     *   <li>{@code X-CounterpartyCode} — from {@link RegionConfig#defaultCounterparty()}</li>
     *   <li>{@code X-ApplicationLanguage} — from {@link RegionConfig#defaultAppLanguage()}</li>
     *   <li>{@code acceptLanguage} — {@code "en"}</li>
     * </ul>
     *
     * <p>Use {@link #withBrowserHeaders(String, String)} instead when the call site also
     * needs {@code Origin}/{@code Referer}/{@code Sec-Fetch-*}/{@code Priority}/etc.
     *
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withRegionHeaders() {
        LOGGER.debug("Adding region headers for chain: {}", name);
        RegionConfig region = RegionConfig.active();
        return withHeader("X-CounterpartyCode", region.defaultCounterparty())
                .withHeader("X-ApplicationLanguage", region.defaultAppLanguage())
                .withHeader("acceptLanguage", "en");
    }

    /**
     * Adds a check to the request.
     *
     * @param check The check to add
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withCheck(CheckBuilder check) {
        LOGGER.debug("Adding check for chain: {}", name);
        this.checks.add(check);
        return this;
    }

    /**
     * Adds multiple checks to the request.
     *
     * @param checks Array of checks to add
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withChecks(CheckBuilder... checks) {
        LOGGER.debug("Adding {} checks for chain: {}", checks.length, name);
        this.checks.addAll(List.of(checks));
        return this;
    }

    /**
     * Saves a value from a JSON path to a session variable.
     *
     * @param jsonPath   The JSON path expression
     * @param sessionKey The key to save the value as in the session
     * @return this instance for fluent API
     */
    public ChainBuilderFactory saveAs(String jsonPath, String sessionKey) {
        LOGGER.debug("Adding saveAs check for chain: {}. JSON Path: {}, Session Key: {}", name, jsonPath, sessionKey);
        this.checks.add(
                jsonPath(jsonPath)
                        .exists()
                        .saveAs(sessionKey)
        );
        return this;
    }

    /**
     * Saves a value from a JSON path to a session variable with validation.
     *
     * @param jsonPath      The JSON path expression
     * @param sessionKey    The key to save the value as in the session
     * @param expectedValue The expected value to validate against
     * @return this instance for fluent API
     */
    public ChainBuilderFactory saveAs(String jsonPath, String sessionKey, String expectedValue) {
        LOGGER.debug("Adding saveAs check with validation for chain: {}. JSON Path: {}, Session Key: {}, Expected Value: {}",
                name, jsonPath, sessionKey, expectedValue);
        this.checks.add(
                jsonPath(jsonPath)
                        .is(expectedValue)
                        .saveAs(sessionKey)
        );
        return this;
    }

    /**
     * Adds a feeder to the request.
     *
     * @param feeder The feeder to add
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withFeeder(FeederBuilder<?> feeder) {
        LOGGER.debug("Adding feeder for chain: {}", name);
        this.feeder = feeder;
        return this;
    }

    /**
     * Builds the final ChainBuilder with all configured options.
     *
     * @return A ChainBuilder instance
     * @throws IllegalStateException if method or path is not set
     */
    public ChainBuilder build() {
        LOGGER.info("Building chain: {}", name);

        if (method == null || path == null) {
            String error = "Method and path must be set before building the chain";
            LOGGER.error("{} for chain: {}", error, name);
            throw new IllegalStateException(error);
        }

        boolean sendMultipart = useMultipartFormEncoding || !multipartParts.isEmpty();

        if (sendMultipart && (body != null || bodyFile != null || dynamicBody != null)) {
            throw new IllegalStateException(
                    "Cannot combine multipart/form-data encoding with a JSON/string body"
                            + " (withBody / withBodyFromFile / withDynamicBody) for chain: "
                            + name);
        }

        LOGGER.debug("Building {} {} request for chain: {}", method, path, name);
        HttpRequestActionBuilder request;
        switch (method) {
            case GET -> request = http(name).get(path);
            case POST -> request = http(name).post(path);
            case PUT -> request = http(name).put(path);
            case DELETE -> request = http(name).delete(path);
            case PATCH -> request = http(name).patch(path);
            case HEAD -> request = http(name).head(path);
            case OPTIONS -> request = http(name).options(path);
            default -> {
                String error = "Unsupported HTTP method: " + method;
                LOGGER.error("{} for chain: {}", error, name);
                throw new IllegalStateException(error);
            }
        }

        if (!headers.isEmpty()) {
            LOGGER.debug("Adding headers for chain: {}. Headers: {}", name, headers);
            request = request.headers(headers);
        }

        if (!dynamicHeaders.isEmpty()) {
            for (var e : dynamicHeaders.entrySet()) {
                request = request.header(e.getKey(), e.getValue());
            }
        }

        if (sendMultipart) {
            List<BodyPart> parts = new ArrayList<>();
            if (!formParams.isEmpty()) {
                LOGGER.debug("Adding multipart text parts from form params for chain: {}: {}", name, formParams.keySet());
                for (Map.Entry<String, Object> e : formParams.entrySet()) {
                    parts.add(StringBodyPart(e.getKey(), String.valueOf(e.getValue())));
                }
            }
            parts.addAll(multipartParts);
            if (parts.isEmpty()) {
                throw new IllegalStateException(
                        "multipart/form-data requires at least one form field or multipart part; chain: "
                                + name);
            }
            LOGGER.debug("Applying Gatling multipart: bodyParts ({}) then asMultipartForm() for chain: {}", parts.size(), name);
            request = request.bodyParts(parts).asMultipartForm();
        } else if (body != null) {
            LOGGER.debug("Adding body for chain: {}. Body: {}", name, body);
            request = request.body(StringBody(body));
        } else if (bodyFile != null) {
            LOGGER.debug("Adding body from file for chain: {}. File: {}", name, bodyFile);
            request = request.body(ElFileBody("bodies/" + bodyFile));
        } else if (dynamicBody != null) {
            LOGGER.debug("Adding dynamic body for chain: {}", name);
            request = request.body(StringBody(dynamicBody));
        }

        if (!formParams.isEmpty() && !sendMultipart) {
            LOGGER.debug("Adding form params for chain: {}. Params: {}", name, formParams);
            request = request.formParamMap(formParams);
        }

        if (disableFollowRedirect) {
            LOGGER.debug("Disabling follow redirect for chain: {}", name);
            request = request.disableFollowRedirect();
        }

        if (!checks.isEmpty()) {
            LOGGER.debug("Adding {} checks for chain: {}", checks.size(), name);
            request = request.check(checks.toArray(new CheckBuilder[0]));
        }

        LOGGER.info("Successfully built chain: {}", name);
        ChainBuilder chain = exec(request);
        if (feeder != null) {
            return feed(feeder).exec(chain);
        }
        return chain;
    }

    // Convenience methods for common HTTP methods
    public ChainBuilderFactory get(String path) {
        LOGGER.debug("Creating GET request for chain: {} with path: {}", name, path);
        return request(HttpMethod.GET, path);
    }

    public ChainBuilderFactory post(String path) {
        LOGGER.debug("Creating POST request for chain: {} with path: {}", name, path);
        return request(HttpMethod.POST, path);
    }

    public ChainBuilderFactory put(String path) {
        LOGGER.debug("Creating PUT request for chain: {} with path: {}", name, path);
        return request(HttpMethod.PUT, path);
    }

    public ChainBuilderFactory delete(String path) {
        LOGGER.debug("Creating DELETE request for chain: {} with path: {}", name, path);
        return request(HttpMethod.DELETE, path);
    }

    public ChainBuilderFactory patch(String path) {
        LOGGER.debug("Creating PATCH request for chain: {} with path: {}", name, path);
        return request(HttpMethod.PATCH, path);
    }

    public ChainBuilderFactory get(ApiPath path) {
        LOGGER.debug("Creating GET request for chain: {} with path: {}", name, path);
        return request(HttpMethod.GET, path.toString());
    }

    public ChainBuilderFactory post(ApiPath path) {
        LOGGER.debug("Creating POST request for chain: {} with path: {}", name, path);
        return request(HttpMethod.POST, path.toString());
    }

    public ChainBuilderFactory put(ApiPath path) {
        LOGGER.debug("Creating PUT request for chain: {} with path: {}", name, path);
        return request(HttpMethod.PUT, path.toString());
    }

    public ChainBuilderFactory delete(ApiPath path) {
        LOGGER.debug("Creating DELETE request for chain: {} with path: {}", name, path);
        return request(HttpMethod.DELETE, path.toString());
    }

    public ChainBuilderFactory patch(ApiPath path) {
        LOGGER.debug("Creating PATCH request for chain: {} with path: {}", name, path);
        return request(HttpMethod.PATCH, path.toString());
    }

    // -------------------------------------------------------------------------
    // Extended Check Methods
    // -------------------------------------------------------------------------

    /**
     * Asserts the HTTP response status code equals the expected value.
     *
     * @param expectedStatus the expected HTTP status (e.g. 200, 201, 404)
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withStatusCheck(int expectedStatus) {
        LOGGER.debug("Adding status check (expected: {}) for chain: {}", expectedStatus, name);
        return withCheck(status().is(expectedStatus));
    }

    /**
     * Asserts the HTTP response status code equals the expected enum value.
     *
     * @param expectedStatus the expected HTTP status enum
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withStatusCheck(StatusCode expectedStatus) {
        return withStatusCheck(expectedStatus.getCode());
    }

    /**
     * Asserts the response body string contains the given substring.
     *
     * @param text that must appear somewhere in the response body
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withBodyContains(String text) {
        LOGGER.debug("Adding substring check ('{}') for chain: {}", text, name);
        return withCheck(substring(text).exists());
    }

    /**
     * Extracts a value from the response body using a regular expression
     * capture group and stores it in the Gatling session.
     *
     * @param regex      the regular expression (first capture group is extracted)
     * @param sessionKey the session variable name to store the extracted value
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withRegexExtract(String regex, String sessionKey) {
        LOGGER.debug("Adding regex extract ('{}' -> '{}') for chain: {}", regex, sessionKey, name);
        return withCheck(regex(regex).saveAs(sessionKey));
    }

    /**
     * Asserts that the given response header is present.
     *
     * @param headerName the name of the HTTP response header
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withHeaderCheck(String headerName) {
        LOGGER.debug("Adding header exists check ('{}') for chain: {}", headerName, name);
        return withCheck(header(headerName).exists());
    }

    /**
     * Extracts a response header value and stores it in the Gatling session.
     *
     * @param headerName the name of the HTTP response header
     * @param sessionKey the session variable name to store the header value
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withHeaderExtract(String headerName, String sessionKey) {
        LOGGER.debug("Adding header extract ('{}' -> '{}') for chain: {}", headerName, sessionKey, name);
        return withCheck(header(headerName).saveAs(sessionKey));
    }

    /**
     * Asserts that the value at the given JSON path equals the expected value.
     *
     * <p>Example: {@code .withJsonPathCheck("$.merchantStatus", "VERIFIED")}
     *
     * @param jsonPathExpr  the JSON path expression
     * @param expectedValue the expected value at that path
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withJsonPathCheck(String jsonPathExpr, String expectedValue) {
        LOGGER.debug("Adding jsonPath check ('{}' == '{}') for chain: {}", jsonPathExpr, expectedValue, name);
        return withCheck(jsonPath(jsonPathExpr).is(expectedValue));
    }

    /**
     * Asserts that the value at the given JSON path equals a Gatling EL expression
     * (typically a session variable).
     *
     * <p>Example: {@code .withJsonPathCheckEL("$.order.orderId", "#{pgwOrderId}")}
     *
     * @param jsonPathExpr       the JSON path expression
     * @param expectedExpression the Gatling EL string (e.g. {@code "#{sessionKey}"})
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withJsonPathCheckEL(String jsonPathExpr, String expectedExpression) {
        LOGGER.debug("Adding jsonPath EL check ('{}' == '{}') for chain: {}", jsonPathExpr, expectedExpression, name);
        return withCheck(jsonPath(jsonPathExpr).isEL(expectedExpression));
    }

    /**
     * Extracts a String value from the response using a JSON path expression
     * and stores it in the Gatling session.
     *
     * <p>Example: {@code .withJsonPathString("$.token", "accessToken")}
     *
     * @param jsonPathExpr the JSON path expression
     * @param sessionKey   the session variable name
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withJsonPathString(String jsonPathExpr, String sessionKey) {
        LOGGER.debug("Adding String jsonPath ('{}' -> '{}') for chain: {}", jsonPathExpr, sessionKey, name);
        return withCheck(jsonPath(jsonPathExpr).ofString().saveAs(sessionKey));
    }

    /**
     * Extracts a nullable String value from the response using a JSON path expression.
     * If the value is null, the default value is stored instead.
     *
     * <p>Example: {@code .withJsonPathStringNullable("$.subName", "productSubName", "")}
     *
     * @param jsonPathExpr the JSON path expression
     * @param sessionKey   the session variable name
     * @param defaultValue the value to use if the JSON value is null
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withJsonPathStringNullable(String jsonPathExpr, String sessionKey, String defaultValue) {
        LOGGER.debug("Adding nullable String jsonPath ('{}' -> '{}', default: '{}') for chain: {}",
                jsonPathExpr, sessionKey, defaultValue, name);
        return withCheck(jsonPath(jsonPathExpr).ofString().withDefault(defaultValue).saveAs(sessionKey));
    }

    /**
     * Extracts an Integer value from the response using a JSON path expression
     * and stores it in the Gatling session.
     *
     * <p>Example: {@code .withJsonPathInt("$.data.id", "userId")}
     *
     * @param jsonPathExpr the JSON path expression
     * @param sessionKey   the session variable name
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withJsonPathInt(String jsonPathExpr, String sessionKey) {
        LOGGER.debug("Adding Integer jsonPath ('{}' -> '{}') for chain: {}", jsonPathExpr, sessionKey, name);
        return withCheck(jsonPath(jsonPathExpr).ofInt().saveAs(sessionKey));
    }

    /**
     * Extracts a Double value from the response using a JSON path expression
     * and stores it in the Gatling session.
     *
     * <p>Example: {@code .withJsonPathDouble("$.price", "itemPrice")}
     *
     * @param jsonPathExpr the JSON path expression
     * @param sessionKey   the session variable name
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withJsonPathDouble(String jsonPathExpr, String sessionKey) {
        LOGGER.debug("Adding Double jsonPath ('{}' -> '{}') for chain: {}", jsonPathExpr, sessionKey, name);
        return withCheck(jsonPath(jsonPathExpr).ofDouble().saveAs(sessionKey));
    }

    /**
     * Extracts a JSON object from the response using a JSON path expression,
     * serializes it back to a JSON string, and stores it in the Gatling session.
     *
     * <p>Use this when you need to extract a nested JSON object and embed it
     * in another JSON template. The value will be stored as a valid JSON string.
     *
     * <p>Example: {@code .withJsonPathObject("$.products[0].fullProductInfo", "productInfo")}
     *
     * @param jsonPathExpr the JSON path expression pointing to a JSON object
     * @param sessionKey   the session variable name
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withJsonPathObject(String jsonPathExpr, String sessionKey) {
        LOGGER.debug("Adding Object jsonPath ('{}' -> '{}') for chain: {}", jsonPathExpr, sessionKey, name);
        return withCheck(jsonPath(jsonPathExpr).ofObject().transform(obj -> {
            try {
                return JSON_MAPPER.writeValueAsString(obj);
            } catch (JsonProcessingException e) {
                LOGGER.error("Failed to serialize JSON object for key '{}': {}", sessionKey, e.getMessage());
                return obj.toString();
            }
        }).saveAs(sessionKey));
    }

    public ChainBuilderFactory withJsonPathArray(String jsonPathExpr, String sessionKey) {
        LOGGER.debug(
                "Extracting JSON array ('{}' -> '{}') for chain: {}",
                jsonPathExpr,
                sessionKey,
                name
        );

        return withCheck(
                jsonPath(jsonPathExpr)
                        .findAll()
                        .transform(values -> {
                            try {
                                return JSON_MAPPER.writeValueAsString(values);
                            } catch (JsonProcessingException e) {
                                LOGGER.error(
                                        "Failed to serialize JSON array for key '{}': {}",
                                        sessionKey,
                                        e.getMessage()
                                );
                                throw new RuntimeException(e);
                            }
                        })
                        .saveAs(sessionKey)
        );
    }

    /**
     * Extracts the entire response body as a String (trimmed) and stores it in the Gatling session.
     *
     * <p>Example: {@code .withBodyStringExtract("rawResponse")}
     *
     * @param sessionKey the session variable name
     * @return this instance for fluent API
     */
    public ChainBuilderFactory withBodyStringExtract(String sessionKey) {
        LOGGER.debug("Adding body string extract ('{}') for chain: {}", sessionKey, name);
        return withCheck(bodyString().transform(String::trim).saveAs(sessionKey));
    }

} 