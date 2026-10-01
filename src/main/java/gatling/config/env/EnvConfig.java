package gatling.config.env;

import gatling.config.enums.BaseURI;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Environment configuration for resolving the active test environment dynamically.
 * Reads {@code -Denv=test} or {@code -Denv=preprod} to configure base URIs, and
 * any generic environmental variables throughout the project.
 *
 * <p>Each environment carries an {@link EnvBaseUris} pair (portal + apex) so
 * callers select the correct host explicitly:
 *
 * <pre>{@code
 * // Portal / Public-API flows:
 * ApiHost portal = EnvConfig.active().baseUri().portal();
 *
 * // APEX / POS flows:
 * ApiHost apex = EnvConfig.active().baseUri().apex();
 * }</pre>
 */
public record EnvConfig(
        String name,
        EnvBaseUris baseUri

        // Additional environment-specific properties (DB urls, auth hubs, etc) can be added here
) {

    public static final EnvConfig TEST = new EnvConfig(
            "test",
            new EnvBaseUris(
                    BaseURI.KSA.PORTAL_TEST,
                    BaseURI.KSA.APEX_TEST,
                    BaseURI.KSA.PGW_TEST,
                    BaseURI.KSA.API_APEX_TEST,
                    BaseURI.KSA.GSDK_API_TEST));
    public static final EnvConfig PREPROD = new EnvConfig(
            "preprod",
            new EnvBaseUris(
                    BaseURI.KSA.PORTAL_PREPROD,
                    BaseURI.KSA.APEX_PREPROD,
                    BaseURI.KSA.PGW_PREPROD,
                    BaseURI.KSA.API_APEX_PREPROD,
                    BaseURI.KSA.GSDK_API_PREPROD));
    public static final EnvConfig PROD = new EnvConfig(
            "prod",
            new EnvBaseUris(
                    BaseURI.KSA.PORTAL_PROD,
                    BaseURI.KSA.APEX_PROD,
                    BaseURI.KSA.PGW_PROD,
                    BaseURI.KSA.API_APEX_PROD,
                    BaseURI.KSA.GSDK_API_PROD));

    /**
     * Resolves the active environment from the {@code env} system property.
     * Default fallback is {@code test}.
     *
     * @return The active {@link EnvConfig}
     */
    public static EnvConfig active() {
        String env = System.getProperty("env", "preprod").toLowerCase();
        return switch (env) {
            case "preprod" -> PREPROD;
            case "prod" -> PROD;
            default -> TEST;
        };
    }

    /**
     * {@code Origin} for browser-style portal calls: maps {@code api.&lt;host&gt;} to
     * {@code https://www.&lt;host-without-api-prefix&gt;} (same scheme and port as {@link #baseUri}).
     * Falls back to the API base URI when the host does not start with {@code api.}.
     */
    public String wwwPortalOrigin() {
        return wwwPortalUri().toString();
    }

    /**
     * {@code Referer} matching typical portal flows ({@code origin + "/"}).
     */
    public String wwwPortalReferer() {
        return wwwPortalOrigin() + "/";
    }

    /**
     * WWW portal origin for GSDK Configuration ({@code /portal/api/v1/...}), matching
     * Postman {@code portal_base_url}. Derived from {@link EnvBaseUris#gsdkApi()} so Old Stack
     * flows do not depend on the portal {@code /aws} host.
     */
    public String gsdkWwwPortalOrigin() {
        return gsdkWwwPortalUri().toString();
    }

    private URI wwwPortalUri() {
        return toWwwHost(baseUri.portal().uri());
    }

    private URI gsdkWwwPortalUri() {
        return toWwwHost(baseUri.gsdkApi().uri());
    }

    private static URI toWwwHost(URI u) {
        String host = u.getHost();
        if (host != null && host.startsWith("api.")) {
            String wwwHost = "www." + host.substring(4);
            try {
                return new URI(u.getScheme(), null, wwwHost, u.getPort(), null, null, null);
            } catch (URISyntaxException e) {
                throw new IllegalStateException("Invalid www portal URI derived from: " + u, e);
            }
        }
        return u;
    }
}
