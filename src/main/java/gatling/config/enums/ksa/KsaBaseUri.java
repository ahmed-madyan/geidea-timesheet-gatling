package gatling.config.enums.ksa;

import gatling.config.enums.ApiHost;

import lombok.Getter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

public enum KsaBaseUri implements ApiHost {

    // -------------------------------------------------------------------------
    // Merchant Portal / Public API hosts (used by portal, onboarding, reports,
    // dashboards, transactions, OIDC token endpoints, etc.)
    // -------------------------------------------------------------------------
    /**
     * Portal — TEST environment.
     */
    PORTAL_TEST("https://api.test.geidea.net"),
    /**
     * Portal — PREPROD environment.
     */
    PORTAL_PREPROD("https://api.gd-pprod-infra.net/aws"),
    /**
     * Portal — PROD environment.
     */
    PORTAL_PROD("https://api.prod.to-zi.com"),

    // -------------------------------------------------------------------------
    // APEX hosts (POS / acquiring stack).
    // -------------------------------------------------------------------------
    /**
     * APEX — TEST environment. TODO: confirm official APEX TEST host.
     */
    APEX_TEST("https://apex.test.geidea.net"),
    /**
     * APEX — PREPROD environment.
     */
    APEX_PREPROD("https://apex.gd-pprod-infra.net"),
    /**
     * APEX — PROD environment. TODO: confirm official APEX PROD host.
     */
    APEX_PROD("https://apex.prod.to-zi.com"),

    // -------------------------------------------------------------------------
    // APEX API hosts (merchant-inquiry / merchant-portal back-end APIs).
    // Distinct from the APEX SPA hosts above (apex.*) — these expose REST
    // endpoints consumed by the merchant portal SPA (e.g. {@code /api/merchant-inquiry/*}).
    // -------------------------------------------------------------------------
    /**
     * APEX API — TEST environment. TODO: confirm official APEX API TEST host.
     */
    API_APEX_TEST("https://api-apex.test.geidea.net"),
    /**
     * APEX API — PREPROD environment. Used by merchant-inquiry endpoints
     * (e.g. {@code POST /api/merchant-inquiry/getTransaction}).
     */
    API_APEX_PREPROD("https://api-apex.gd-pprod-infra.net"),
    /**
     * APEX API — PROD environment. TODO: confirm official APEX API PROD host.
     */
    API_APEX_PROD("https://api-apex.prod.to-zi.com"),

    // -------------------------------------------------------------------------
    // Specialised hosts that are not part of the portal/apex pair.
    // -------------------------------------------------------------------------
    /**
     * KSA Payment Gateway (Direct); matches {@code postman/environments/KSA_PGW_Preprod.postman_environment.json} {@code baseUrl}.
     */
    PGW_TEST("https://api.gd-pprod-infra.net"),
    PGW_PREPROD("https://api.gd-pprod-infra.net"),
    PGW_PROD("https://api.gd-pprod-infra.net"),

    // -------------------------------------------------------------------------
    // GSDK / Old Stack Public API hosts (no {@code /aws} prefix).
    // Matches Postman {@code PreProdUrl} in KSA Old Stack environments.
    // -------------------------------------------------------------------------
    /** GSDK / Old Stack API — TEST. */
    GSDK_API_TEST("https://api.test.geidea.net"),
    /** GSDK / Old Stack API — PREPROD ({@code PreProdUrl}). */
    GSDK_API_PREPROD("https://api.gd-pprod-infra.net"),
    /** GSDK / Old Stack API — PROD. */
    GSDK_API_PROD("https://api.prod.to-zi.com");

    @Getter
    private final URI baseURI;

    KsaBaseUri(String uriString) {
        try {
            URI parsed = new URI(uriString);
            if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null) {
                throw new IllegalArgumentException("KsaBaseUri must use https and contain a valid host: " + uriString);
            }
            this.baseURI = parsed;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI format: " + uriString, e);
        }
    }

    @Override
    public String toString() {
        return baseURI.toString(); // Allows use like: String url = BaseURI.PORTAL_TEST.toString();
    }

    public String value() {
        return baseURI.toString();
    }

    @Override
    public URI uri() {
        return baseURI;
    }

    public static KsaBaseUri fromString(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("KsaBaseUri name must not be null or empty");
        }
        return Arrays.stream(values())
                .filter(uri -> uri.name().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown KsaBaseUri: " + name));
    }
}