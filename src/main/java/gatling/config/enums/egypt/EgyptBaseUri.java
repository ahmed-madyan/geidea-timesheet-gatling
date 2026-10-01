package gatling.config.enums.egypt;

import gatling.config.enums.ApiHost;

import lombok.Getter;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

public enum EgyptBaseUri implements ApiHost {

    // -------------------------------------------------------------------------
    // Merchant Portal / Public API hosts (used by portal, onboarding, reports,
    // dashboards, transactions, OIDC token endpoints, etc.)
    // -------------------------------------------------------------------------
    /**
     * Portal — TEST environment.
     */
    PORTAL_TEST(""),
    /**
     * Portal — PREPROD environment.
     */
    PORTAL_PREPROD(""),
    /**
     * Portal — PROD environment.
     */
    PORTAL_PROD(""),

    // -------------------------------------------------------------------------
    // APEX hosts (POS / acquiring stack).
    // -------------------------------------------------------------------------
    /**
     * APEX — TEST environment. TODO: confirm official APEX TEST host.
     */
    APEX_TEST(""),
    /**
     * APEX — PREPROD environment.
     */
    APEX_PREPROD(""),
    /**
     * APEX — PROD environment. TODO: confirm official APEX PROD host.
     */
    APEX_PROD(""),

    // -------------------------------------------------------------------------
    // APEX API hosts (merchant-inquiry / merchant-portal back-end APIs).
    // Distinct from the APEX SPA hosts above (apex.*) — these expose REST
    // endpoints consumed by the merchant portal SPA (e.g. {@code /api/merchant-inquiry/*}).
    // -------------------------------------------------------------------------
    /**
     * APEX API — TEST environment. TODO: confirm official APEX API TEST host.
     */
    API_APEX_TEST(""),
    /**
     * APEX API — PREPROD environment. Used by merchant-inquiry endpoints
     * (e.g. {@code POST /api/merchant-inquiry/getTransaction}).
     */
    API_APEX_PREPROD(""),
    /**
     * APEX API — PROD environment. TODO: confirm official APEX API PROD host.
     */
    API_APEX_PROD(""),

    // -------------------------------------------------------------------------
    // Specialised hosts that are not part of the portal/apex pair.
    // -------------------------------------------------------------------------
    /**
     * KSA Payment Gateway (Direct); matches {@code postman/environments/KSA_PGW_Preprod.postman_environment.json} {@code baseUrl}.
     */
    PGW_TEST(""),
    PGW_PREPROD(""),
    PGW_PROD(""),

    // -------------------------------------------------------------------------
    // GSDK / Old Stack Public API hosts (no {@code /aws} prefix).
    // Matches Postman {@code PreProdUrl} in KSA Old Stack environments.
    // -------------------------------------------------------------------------
    /** GSDK / Old Stack API — TEST. */
    GSDK_API_TEST(""),
    /** GSDK / Old Stack API — PREPROD ({@code PreProdUrl}). */
    GSDK_API_PREPROD(""),
    /** GSDK / Old Stack API — PROD. */
    GSDK_API_PROD("");

    @Getter
    private final URI baseURI;

    EgyptBaseUri(String uriString) {
        if (uriString == null || uriString.isBlank()) {
            this.baseURI = URI.create("");
            return;
        }
        try {
            URI parsed = new URI(uriString);
            if (!"https".equalsIgnoreCase(parsed.getScheme()) || parsed.getHost() == null) {
                throw new IllegalArgumentException("EgyptBaseUri must use https and contain a valid host: " + uriString);
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

    public static EgyptBaseUri fromString(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("");
        }
        return Arrays.stream(values())
                .filter(uri -> uri.name().equalsIgnoreCase(name.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown EgyptBaseUri: " + name));
    }
}