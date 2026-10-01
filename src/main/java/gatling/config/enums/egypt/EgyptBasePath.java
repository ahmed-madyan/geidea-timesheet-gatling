package gatling.config.enums.egypt;

import gatling.config.enums.ApiPath;

import lombok.Getter;

/**
 * Centralised API path constants for all Geidea services.
 *
 * <p>Replaces the seven scattered {@code BasePath} enums that previously
 * existed per-package with identical boilerplate. Each constant holds a
 * complete URI path that is appended to the environment {@code BaseURI}.
 *
 * <p>Usage:
 * <pre>{@code
 * .get(BasePath.FLOW_STEPS_PGT)
 * .post(BasePath.DUE_DILIGENCE_LEAD_CREATE)
 * }</pre>
 */
@Getter
public enum EgyptBasePath implements ApiPath {

    // ── Authentication ───────────────────────────────────────────
    /**
     * Keycloak token endpoint — replace {@code {REALM}} at runtime.
     */
    AUTH_TOKEN(""),
    KEYCLOAK_ADMIN_USERS_SEARCH(""),
    KEYCLOAK_ADMIN_USER_BY_ID(""),
    // ── Onboarding Base Paths ────────────────────────────────────
    SELF_ONBOARDING(""),
    OTP(""),

    // ── Step 01: Create Lead ─────────────────────────────────────
    FLOW_STEPS_PGT(""),
    ADMIN_PANEL_GET_BUSINESS_TYPES(""),
    DUE_DILIGENCE_LEAD_CREATE(""),
    DUE_DILIGENCE_BUSINESS_INFO_CHECK(""),
    DUE_DILIGENCE_ONBOARDING_SPL_VERIFICATION(""),
    // ── Step 02: Verify Phone ────────────────────────────────────
    OTP_ONBOARDING_PHONE_VERIFY(""),
    OTP_ONBOARDING_PHONE_CONFIRM(""),
    DUE_DILIGENCE_ELM_CHECK(""),

    // ── Step 03: Create User ─────────────────────────────────────
    MCC(""),
    USERS_CREATE(""),
    VERIFICATION_EMAIL_VERIFY(""),

    // ── Step 04: Product Selection ───────────────────────────────
    PRODUCT_FULL(""),
    CART_SUMMARY(""),
    CART(""),

    // ── Step 05: Business Details ────────────────────────────────
    FLOW_STEPS_GET_BUSINESS_DETAILS(""),
    FLOW_STEPS_SAVE_BUSINESS_DETAILS(""),

    // ── Step 06: Locations ───────────────────────────────────────
    FLOW_STEPS_LOCATIONS(""),
    FLOW_STEPS_CART_GET_AVAILABLE_PRODUCTS(""),
    FLOW_STEPS_LOCATIONS_SAVE(""),

    // ── Step 07: Payout Details ──────────────────────────────────
    DUE_DILIGENCE_CHECK_IBAN(""),
    FLOW_STEPS_SAVE_PAYOUT_DETAILS(""),

    // ── Step 08: PEP Declaration ─────────────────────────────────
    FLOW_STEPS_CREATE_PEP(""),

    // ── Step 09: Review & Confirm ────────────────────────────────
    FLOW_STEPS_REVIEW_CONFIRM_CHECK(""),
    DUE_DILIGENCE_NAFATH_AUTH(""),
    DUE_DILIGENCE_NAFATH_VALIDATE(""),
    FLOW_STEPS_REVIEW_CONFIRM(""),

    // ── Step 10: Payment ─────────────────────────────────────────
    PAYMENT_INITIATE(""),
    PAYMENT_PAY(""),

    // ── Step 11: Contract ────────────────────────────────────────
    CONTRACT(""),
    CONTRACT_SET_VIEWED(""),
    CONTRACT_VERIFY_OTP(""),
    CONTRACT_CONFIRM_OTP(""),
    CONTRACT_SIGN(""),



    // ── Merchant Import Onboarding (KSA) ──────────────────────────
    LEAD_USER_CREATE(""),
    MERCHANTS_IMPORT_ONBOARD(""),

    // ── Apex / Transactions / Payouts ─────────────────────────────
    CREATE_TRANSACTION(""),

    // ── paymentGateway Direct (card-not-present) ─────────────────────────────
    /**
     * Payment Intent — create direct session (preauth / pay / void / refund flows).
     */
    PAYMENT_INTENT_DIRECT_SESSION(""),
    /**
     * paymentGateway v6 — start 3DS authentication.
     */
    PGW_AUTHENTICATE_INITIATE(""),
    /**
     * paymentGateway v6 — complete payer authentication.
     */
    PGW_AUTHENTICATE_PAYER(""),
    /**
     * paymentGateway v2 — direct pay / preauthorize.
     */
    PGW_DIRECT_PAY(""),
    /**
     * paymentGateway v2 — capture a prior preauthorization.
     */
    PGW_DIRECT_CAPTURE(""),
    /**
     * paymentGateway v2 — refund a prior payment.
     */
    PGW_DIRECT_REFUND(""),
    /**
     * paymentGateway v4 — void a prior preauthorization.
     */
    PGW_DIRECT_VOID(""),
    PGW_DIRECT_GET_ORDER(""),
    PGW_DIRECT_SEARCH_TRANSACTIONS(""),
    PGW_PORTALS_GET_ORDER(""),
    // ── Apex Merchant Onboarding API ──────────────────────────────
    /**
     * Apex merchant-onboarding — create/upsert a merchant record (action {@code A}). Same
     * endpoint is used for both Level-2 (business / parent) and Level-3 (sub-merchant)
     * payloads; the {@code merchantLevel} field and presence of {@code parentMerchantId}
     * distinguish the two records.
     */
    MERCHANT_ONBOARDING_CREATE_MERCHANT(""),

    // ── Apex Merchant Inquiry API ─────────────────────────────────
    /**
     * Merchant Inquiry — search/list previously-created transactions (advanced filter).
     */
    MERCHANT_INQUIRY_GET_TRANSACTION(""),
    /**
     * Merchant Inquiry — dashboard chart data. Body is a non-standard
     * <code>&amp;</code>-separated concatenation of one JSON object per chart (e.g.
     * {@code {"chartCode":"ChartCode1",...}&amp;{"chartCode":"ChartCode2",...}&amp;...}).
     */
    MERCHANT_INQUIRY_GET_CHART(""),
    /**
     * Merchant Inquiry — daily transaction-volume time series for the dashboard.
     */
    MERCHANT_INQUIRY_GET_DAY_GRAPH(""),

    // ── Apex Merchant Portal / Clearing (CAS+AES login + Payouts) ─────────
    /**
     * CAS SSO login (POST credentials, AES-encrypted password).
     */
    APEX_SSO_LOGIN(""),
    /**
     * Merchant SPA shell — entry point that triggers the CAS handshake.
     */
    APEX_MERCHANT_APP(""),
    /**
     * Filter/list previously-uploaded files (NOT an upload). Requires {@code x-csrf-token}.
     */
    APEX_MERCHANT_UPLOAD_FILE_GET(""),
    /**
     * Upload a file (multipart/form-data) — submitted via a hidden iframe form. CSRF is
     * passed as the {@code _csrf} <strong>query parameter</strong>, NOT a header (Spring's
     * {@code HttpSessionCsrfTokenRepository} accepts both). Multipart parts:
     * {@code tablename}, {@code file}, {@code voucherTypeLabel}, {@code isExport}.
     */
    APEX_MERCHANT_UPLOAD_FILE_UPLOAD(""),
    /**
     * Trigger a manual clearing/payouts process run. Callers append the
     * {@code processId / subProcessId / runningDate / mode / sourceBank} query string in
     * addition to sending the same fields as form params (matches the captured cURL).
     */
    APEX_CLEARING_PROCESS_MANUAL_RUN(""),
    /**
     * Fetch process-run header detail (selects a B2B Posting run).
     */
    APEX_CLEARING_GET_PROCESS_DTL(""),

    // ── Merchant Account: Back-Office (admin) ─────────────────────
    BACKOFFICE_MERCHANT_SEARCH(""),
    BACKOFFICE_MERCHANT(""),
    BACKOFFICE_ORDER(""),

    // ── Merchant Account: Due Diligence (compliance) ─────────────
    DUE_DILIGENCE_VALIDATION(""),
    DUE_DILIGENCE_CHECK_MERCHANT(""),
    /**
     * Person-of-interest (principal) check override — path includes {@code merchantId}.
     */
    DUE_DILIGENCE_CHECK_POI(""),
    DUE_DILIGENCE_FIN_SCAN_MERCHANT_BUSINESS(""),

    BACKOFFICE_SHAREHOLDER_INDIVIDUALS(""),
    BACKOFFICE_SHAREHOLDER_INDIVIDUAL_UPDATE(""),

    // ── Merchant Account: Merchant Portal admin ──────────────────
    MERCHANT_PORTAL_MERCHANT_SUMMARY(""),
    MERCHANT_PORTAL_STORE(""),
    MERCHANT_PORTAL_STORE_COMPANY(""),
    MERCHANT_PORTAL_BANK_ACCOUNT(""),
    // ── Merchant Account: Checkout & Product ─────────────────────
    CHECKOUT_ORDER_SEARCH(""),
    CHECKOUT_ORDER(""),
    PRODUCT_ORDER_PRODUCT_INSTANCES(""),
    PRODUCT_TERMINAL_DATA_SET_SEARCH(""),
    PRODUCT_TERMINAL_DATA_SET_IMPORT_AWS(""),
    PRODUCT_INSTANCE(""),
    /**
     * Create a child product instance (e.g. add a payment method under a parent product).
     */
    PRODUCT_INSTANCE_ADD(""),

    // ── Merchant Portal ──────────────────────────────────────────
    /**
     * CMS fragments for portal shell (layout, nav) — onboarding API host.
     */
    CMS_CONTENT_LAYOUT(""),
    CMS_CONTENT_PAYMENT_LINKS(""),
    CMS_CONTENT_ACCOUNT_MANAGEMENT(""),
    CMS_CONTENT_MSR(""),
    CMS_CONTENT_VAT(""),
    CMS_CONTENT_STORES(""),
    /**
     * Reports NS monthly statement detailed — see {@code BaseURI.PORTAL_PREPROD} {@code /aws} prefix.
     */
    REPORTS_MONTHLY_STATEMENT_DETAILED(""),
    /**
     * Reports NS advanced search (v2) — see environments using {@code BaseURI.PORTAL_PREPROD} {@code /aws} prefix.
     */
    REPORTS_ADVANCED_SEARCH(""),
    TOTAL_TRANSACTIONS_ANALYTICS_DATA(""),
    PBL_ANALYTICS_DATA(""),
    CURRENT_USER(""),
    USER_ENTITIES(""),
    TRANSACTIONS(""),
    TRANSACTIONS_PDF(""),
    GENERATE_TRANSACTION_REPORT(""),
    GET_REQUESTED_TRANSACTIONS(""),
    SHOP_PRODUCTS(""),
    SHOP_CART_ITEMS(""),
    SHOP_CART(""),
    SHOP_CART_ITEM_QUANTITY(""),
    SHOP_CART_ASSIGN_ITEM(""),

    // ── GSDK / Old Stack Onboarding ──────────────────────────────
    GSDK_CONFIG_FEATURE_TOGGLES(""),
    GSDK_ONBOARDING_REFDATA(""),
    GSDK_PRODUCT_CATEGORIES(""),
    GSDK_PRODUCT_PRODUCTS(""),
    GSDK_DUE_DILIGENCE_LEAD_VALIDATE(""),
    GSDK_ONBOARDING_LEAD(""),
    GSDK_ONBOARDING_PHONE_VERIFY(""),
    GSDK_ONBOARDING_PHONE_CONFIRM(""),
    GSDK_DUE_DILIGENCE_NAFATH_AUTH(""),
    GSDK_DUE_DILIGENCE_NAFATH_TOKEN(""),
    GSDK_ONBOARDING_LEAD_DETAILS(""),
    GSDK_ONBOARDING_ACCOUNT(""),
    GSDK_PERMISSION_ROLES(""),
    GSDK_MERCHANT_PORTAL_USER_INFO(""),
    GSDK_ACCOUNT_STATE(""),
    GSDK_ONBOARDING_LEAD_MERCHANT(""),
    GSDK_CHECKOUT_CART(""),
    GSDK_CHECKOUT_CART_CHECKOUT(""),
    GSDK_CHECKOUT_CART_CREATE(""),
    GSDK_CHECKOUT_CART_STORE_PRODUCT(""),
    GSDK_MERCHANT_PORTAL_BUSINESS(""),
    GSDK_MERCHANT_PORTAL_BANK_ACCOUNT(""),
    GSDK_MERCHANT_PORTAL_AUDITLOG(""),
    GSDK_MERCHANT_PORTAL_DOCUMENT(""),
    GSDK_MERCHANT_PORTAL_STORE(""),
    GSDK_MERCHANT_PORTAL_STORE_BY_MERCHANT(""),
    GSDK_MERCHANT_PORTAL_NEW_BUSINESS(""),
    GSDK_CONTRACT_VERIFY(""),
    GSDK_CONTRACT_CONFIRM(""),
    GSDK_BACKOFFICE_SHAREHOLDER_INDIVIDUAL(""),
    GSDK_BACKOFFICE_SHAREHOLDER_COMPANY(""),
    GSDK_BACKOFFICE_MERCHANT_COMMENT(""),
    GSDK_PRODUCT_ORDER_INSTANCES(""),
    GSDK_PRODUCT_TERMINAL_DATA_SET(""),
    GSDK_PRODUCT_REGISTER_TABBY(""),
    GSDK_FEDERATION_CONTRACT_LIST(""),
    GSDK_FEDERATION_CONTRACT(""),
    GSDK_FEDERATION_CONTRACT_MAPPINGS(""),
    GSDK_FEDERATION_CONTRACT_REASSIGN(""),
    GSDK_ONBOARDING_HEALTH(""),

    //paymentGateway Login
    PGW_PORTAL_USER_LOGIN(""),


    // ── GSDK Configuration (www portal host — use absolute URL) ──
    GSDK_PORTAL_EXT_USERS_VIEW(""),
    GSDK_PORTAL_EXT_USERS_ORGS(""),
    GSDK_PORTAL_MY_ROLE(""),
    GSDK_PORTAL_FINANCIER_CONTRACT(""),
    GSDK_PORTAL_VALIDATE_REFERRER_RATE(""),
    GSDK_PORTAL_REFERRER_LINK(""),
    GSDK_PORTAL_FINANCIER_CAPS(""),
    GSDK_PORTAL_BANK_PAYOUT_SETTINGS(""),
    GSDK_PORTAL_APPROVE_CHANGE_REQUEST(
            ""),
    GSDK_PORTAL_VERIFY_CHANGE_REQUEST(
            ""),
    GSDK_PORTAL_CUSTOMER_PAYOUT_SETTINGS(
            ""),
    GSDK_PORTAL_ORGANIZATIONS(""),
    GSDK_PORTAL_BANK_TEMPLATE_TRANSFERS(""),
    GSDK_PORTAL_SUBSCRIPTIONS(""),
    GSDK_PORTAL_CUSTOMER_CONTRACT_VIEW(""),
    GSDK_PORTAL_COLLECTION_SETTINGS(
            "");

    private final String value;

    EgyptBasePath(String path) {
        if (path == null || !(path.isEmpty() || path.startsWith("/"))) {
            throw new IllegalArgumentException(
                    "ApiPath must start with '/' and not be null: " + path);
        }
        this.value = path;
    }

    /**
     * Returns the path string — allows seamless use in string concatenation.
     */
    @Override
    public String path() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
