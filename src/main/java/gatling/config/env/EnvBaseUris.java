package gatling.config.env;

import gatling.config.enums.ApiHost;

/**
 * Per-environment base URIs used by the framework.
 *
 * <p>Each {@link EnvConfig} owns one {@code EnvBaseUris} so callers can pick
 * the correct host for the system they are exercising without hard-coding
 * environment names:
 *
 * <pre>{@code
 * // Portal / Public-API flows (merchant portal, onboarding, reports, OIDC):
 * ApiHost portal = EnvConfig.active().baseUri().portal();
 *
 * // APEX / acquiring (POS) flows:
 * ApiHost apex = EnvConfig.active().baseUri().apex();
 *
 * // GSDK / Old Stack (no /aws prefix):
 * ApiHost gsdkApi = EnvConfig.active().baseUri().gsdkApi();
 * }</pre>
 *
 * <p>This indirection keeps simulations environment-agnostic — switching
 * {@code -Denv=test|preprod|prod} swaps all hosts atomically.
 *
 * @param portal         Portal / Public-API host for the active environment.
 * @param apex           APEX (POS / acquiring) SPA host for the active environment.
 * @param paymentGateway KSA paymentGateway (direct) host for the active environment.
 * @param apiApex        APEX REST-API host (e.g. {@code /api/merchant-inquiry/*}) for the
 *                       active environment. Distinct from {@link #apex()}, which serves the
 *                       APEX SPA / SSO endpoints.
 * @param gsdkApi        GSDK / Old Stack Public API host (matches Postman {@code PreProdUrl};
 *                       no {@code /aws} prefix).
 */
public record EnvBaseUris(
        ApiHost portal,
        ApiHost apex,
        ApiHost paymentGateway,
        //ApiHost paymentGateway,
        ApiHost apiApex,
        ApiHost gsdkApi) {
}
