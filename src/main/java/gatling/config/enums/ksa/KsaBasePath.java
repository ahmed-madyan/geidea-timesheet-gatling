package gatling.config.enums.ksa;

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
public enum KsaBasePath implements ApiPath {

    // ── Authentication ───────────────────────────────────────────
    /**
     * Keycloak token endpoint — replace {@code {REALM}} at runtime.
     */
    AUTH_TOKEN("/auth/realms/{REALM}/protocol/openid-connect/token"),
    KEYCLOAK_ADMIN_USERS_SEARCH("/auth/admin/realms/preprod/users?briefRepresentation=true&first=0&max=20&search=#{randomEmail}"),
    KEYCLOAK_ADMIN_USER_BY_ID("/auth/admin/realms/preprod/users/#{keyclockUserId}"),
    // ── Onboarding Base Paths ────────────────────────────────────
    SELF_ONBOARDING("/merchantexperienceonboarding/api/v1"),
    OTP("/api/v1/otp"),

    // ── Step 01: Create Lead ─────────────────────────────────────
    FLOW_STEPS_PGT("/merchantexperienceonboarding/api/v1/FlowSteps/pgt"),
    ADMIN_PANEL_GET_BUSINESS_TYPES("/merchantexperienceonboarding/api/v1/AdminPanel/GetBusinessTypes"),
    DUE_DILIGENCE_LEAD_CREATE("/merchantexperienceonboarding/api/v1/DueDilligence/lead/create"),
    DUE_DILIGENCE_BUSINESS_INFO_CHECK("/merchantexperienceonboarding/api/v1/DueDilligence/BusinessInfoCheck"),
    DUE_DILIGENCE_ONBOARDING_SPL_VERIFICATION("/merchantexperienceonboarding/api/v1/DueDilligence/OnBoarding-SplVerification?leadId=#{leadId}"),
    // ── Step 02: Verify Phone ────────────────────────────────────
    OTP_ONBOARDING_PHONE_VERIFY("/api/v1/otp/onboarding-phone/verify"),
    OTP_ONBOARDING_PHONE_CONFIRM("/api/v1/otp/onboarding-phone/confirm"),
    DUE_DILIGENCE_ELM_CHECK("/api/v1/DueDilligence/elmcheck/#{leadId}"),

    // ── Step 03: Create User ─────────────────────────────────────
    MCC("/merchantexperienceonboarding/api/v1/MCC/#{leadId}"),
    USERS_CREATE("/merchantexperienceonboarding/api/v1/Users/users"),
    VERIFICATION_EMAIL_VERIFY("/merchantexperienceonboarding/api/v1/Verification/email/verify"),

    // ── Step 04: Product Selection ───────────────────────────────
    PRODUCT_FULL("/merchantexperienceonboarding/api/v1/product/full?page=1&pageSize=50"),
    CART_SUMMARY("/merchantexperienceonboarding/api/v1/cart/summary"),
    CART("/merchantexperienceonboarding/api/v1/Cart"),

    // ── Step 05: Business Details ────────────────────────────────
    FLOW_STEPS_GET_BUSINESS_DETAILS("/merchantexperienceonboarding/api/v1/FlowSteps/GetBusinessDetails?leadId=#{leadId}"),
    FLOW_STEPS_SAVE_BUSINESS_DETAILS("/merchantexperienceonboarding/api/v1/FlowSteps/SaveBusinessDetails"),

    // ── Step 06: Locations ───────────────────────────────────────
    FLOW_STEPS_LOCATIONS("/merchantexperienceonboarding/api/v1/FlowSteps/locations/#{leadId}/0/100000"),
    FLOW_STEPS_CART_GET_AVAILABLE_PRODUCTS("/merchantexperienceonboarding/api/v1/FlowSteps/cart/GetAvailableProducts/#{leadId}"),
    FLOW_STEPS_LOCATIONS_SAVE("/merchantexperienceonboarding/api/v1/FlowSteps/locations/save"),

    // ── Step 07: Payout Details ──────────────────────────────────
    DUE_DILIGENCE_CHECK_IBAN("/merchantexperienceonboarding/api/v1/DueDilligence/CheckIban"),
    FLOW_STEPS_SAVE_PAYOUT_DETAILS("/merchantexperienceonboarding/api/v1/FlowSteps/SavePayoutDetails"),

    // ── Step 08: PEP Declaration ─────────────────────────────────
    FLOW_STEPS_CREATE_PEP("/merchantexperienceonboarding/api/v1/FlowSteps/CreatePEPStep"),

    // ── Step 09: Review & Confirm ────────────────────────────────
    FLOW_STEPS_REVIEW_CONFIRM_CHECK("/merchantexperienceonboarding/api/v1/FlowSteps/reviewConfirm/check"),
    DUE_DILIGENCE_NAFATH_AUTH("/merchantexperienceonboarding/api/v1/DueDilligence/nafathauth?NationalId=#{nationalId}"),
    DUE_DILIGENCE_NAFATH_VALIDATE("/merchantexperienceonboarding/api/v1/DueDilligence/nafathvalidate?TransactionId=#{NafathtransactionId}&State=#{leadId}"),
    FLOW_STEPS_REVIEW_CONFIRM("/merchantexperienceonboarding/api/v1/FlowSteps/reviewConfirm?leadId=#{leadId}"),

    // ── Step 10: Payment ─────────────────────────────────────────
    PAYMENT_INITIATE("/merchantexperienceonboarding/api/v1/payment/initiate"),
    PAYMENT_PAY("/merchantexperienceonboarding/api/v1/payment/pay"),

    // ── Step 11: Contract ────────────────────────────────────────
    CONTRACT("/merchantexperienceonboarding/api/v1/Contract"),
    CONTRACT_SET_VIEWED("/merchantexperienceonboarding/api/v1/Contract/setContractViewed"),
    CONTRACT_VERIFY_OTP("/merchantexperienceonboarding/api/v1/Contract/verify-otp"),
    CONTRACT_CONFIRM_OTP("/merchantexperienceonboarding/api/v1/Contract/confirm-otp/#{otp_key}"),
    CONTRACT_SIGN("/merchantexperienceonboarding/api/v1/Contract/sign/#{otp_key}"),



    // ── Merchant Import Onboarding (KSA) ──────────────────────────
    LEAD_USER_CREATE("/onboarding/api/v1/users/create"),
    MERCHANTS_IMPORT_ONBOARD("/onboarding/api/v1/MerchantsImport/onboard?import=true"),

    // ── Apex / Transactions / Payouts ─────────────────────────────
    CREATE_TRANSACTION("/api/transaction/createTransaction"),

    // ── paymentGateway Direct (card-not-present) ─────────────────────────────
    /**
     * Payment Intent — create direct session (preauth / pay / void / refund flows).
     */
    PAYMENT_INTENT_DIRECT_SESSION("/payment-intent/api/v2/direct/session"),
    /**
     * paymentGateway v6 — start 3DS authentication.
     */
    PGW_AUTHENTICATE_INITIATE("/pgw/api/v6/direct/authenticate/initiate"),
    /**
     * paymentGateway v6 — complete payer authentication.
     */
    PGW_AUTHENTICATE_PAYER("/pgw/api/v6/direct/authenticate/payer"),
    /**
     * paymentGateway v2 — direct pay / preauthorize.
     */
    PGW_DIRECT_PAY("/pgw/api/v2/direct/pay"),
    /**
     * paymentGateway v2 — capture a prior preauthorization.
     */
    PGW_DIRECT_CAPTURE("/pgw/api/v2/direct/capture"),
    /**
     * paymentGateway v2 — refund a prior payment.
     */
    PGW_DIRECT_REFUND("/pgw/api/v2/direct/refund"),
    /**
     * paymentGateway v4 — void a prior preauthorization.
     */
    PGW_DIRECT_VOID("/pgw/api/v4/direct/void"),
    PGW_DIRECT_GET_ORDER("/pgw/api/v1/direct/order/#{pgwOrderId}"),
    PGW_DIRECT_SEARCH_TRANSACTIONS("/pgwportalsapi/api/v1/Transaction/Search"),
    PGW_PORTALS_GET_ORDER("/pgwportalsapi/api/v1/order/#{orderId}"),
    // ── Apex Merchant Onboarding API ──────────────────────────────
    /**
     * Apex merchant-onboarding — create/upsert a merchant record (action {@code A}). Same
     * endpoint is used for both Level-2 (business / parent) and Level-3 (sub-merchant)
     * payloads; the {@code merchantLevel} field and presence of {@code parentMerchantId}
     * distinguish the two records.
     */
    MERCHANT_ONBOARDING_CREATE_MERCHANT("/api/merchant-onboarding/createMerchant"),

    // ── Apex Merchant Inquiry API ─────────────────────────────────
    /**
     * Merchant Inquiry — search/list previously-created transactions (advanced filter).
     */
    MERCHANT_INQUIRY_GET_TRANSACTION("/api/merchant-inquiry/getTransaction"),
    /**
     * Merchant Inquiry — dashboard chart data. Body is a non-standard
     * <code>&amp;</code>-separated concatenation of one JSON object per chart (e.g.
     * {@code {"chartCode":"ChartCode1",...}&amp;{"chartCode":"ChartCode2",...}&amp;...}).
     */
    MERCHANT_INQUIRY_GET_CHART("/api/merchant-inquiry/getChart"),
    /**
     * Merchant Inquiry — daily transaction-volume time series for the dashboard.
     */
    MERCHANT_INQUIRY_GET_DAY_GRAPH("/api/merchant-inquiry/getDayGraph"),

    // ── Apex Merchant Portal / Clearing (CAS+AES login + Payouts) ─────────
    /**
     * CAS SSO login (POST credentials, AES-encrypted password).
     */
    APEX_SSO_LOGIN("/sso/login"),
    /**
     * Merchant SPA shell — entry point that triggers the CAS handshake.
     */
    APEX_MERCHANT_APP("/merchant/app/app"),
    /**
     * Filter/list previously-uploaded files (NOT an upload). Requires {@code x-csrf-token}.
     */
    APEX_MERCHANT_UPLOAD_FILE_GET("/merchant/app/upload-file/get"),
    /**
     * Upload a file (multipart/form-data) — submitted via a hidden iframe form. CSRF is
     * passed as the {@code _csrf} <strong>query parameter</strong>, NOT a header (Spring's
     * {@code HttpSessionCsrfTokenRepository} accepts both). Multipart parts:
     * {@code tablename}, {@code file}, {@code voucherTypeLabel}, {@code isExport}.
     */
    APEX_MERCHANT_UPLOAD_FILE_UPLOAD("/merchant/app/upload-file/upload"),
    /**
     * Trigger a manual clearing/payouts process run. Callers append the
     * {@code processId / subProcessId / runningDate / mode / sourceBank} query string in
     * addition to sending the same fields as form params (matches the captured cURL).
     */
    APEX_CLEARING_PROCESS_MANUAL_RUN("/clearing/app/business/localconf/processManualRunFromGUI"),
    /**
     * Fetch process-run header detail (selects a B2B Posting run).
     */
    APEX_CLEARING_GET_PROCESS_DTL("/clearing/app/business/localconf/getProcessDtl"),

    // ── Merchant Account: Back-Office (admin) ─────────────────────
    BACKOFFICE_MERCHANT_SEARCH("/backoffice/api/v1/merchant/advancedSearch"),
    BACKOFFICE_MERCHANT("/backoffice/api/v1/merchant/#{merchantId}"),
    BACKOFFICE_ORDER("/backoffice/api/v1/order/#{orderId}"),

    // ── Merchant Account: Due Diligence (compliance) ─────────────
    DUE_DILIGENCE_VALIDATION("/due-diligence/api/v1/validation/#{merchantId}"),
    DUE_DILIGENCE_CHECK_MERCHANT("/due-diligence/api/v1/check/merchant"),
    /**
     * Person-of-interest (principal) check override — path includes {@code merchantId}.
     */
    DUE_DILIGENCE_CHECK_POI("/due-diligence/api/v1/check/poi/#{merchantId}"),
    DUE_DILIGENCE_FIN_SCAN_MERCHANT_BUSINESS("/due-diligence/api/v1/Finscan/MerchantBuisness"),

    BACKOFFICE_SHAREHOLDER_INDIVIDUALS("/backoffice/api/v1/shareholder/individuals/#{merchantId}"),
    BACKOFFICE_SHAREHOLDER_INDIVIDUAL_UPDATE("/backoffice/api/v1/Shareholder/individual/update"),

    // ── Merchant Account: Merchant Portal admin ──────────────────
    MERCHANT_PORTAL_MERCHANT_SUMMARY("/merchant-portal/api/v1/Merchant/#{merchantId}/summary"),
    MERCHANT_PORTAL_STORE("/merchant-portal/api/v1/Store/#{storeId}"),
    MERCHANT_PORTAL_STORE_COMPANY("/merchant-portal/api/v1/Store/#{storeId}/#{companyId}"),
    MERCHANT_PORTAL_BANK_ACCOUNT("/merchant-portal/api/v1/BankAccount?#{companyId}&isPassed=true"),
    // ── Merchant Account: Checkout & Product ─────────────────────
    CHECKOUT_ORDER_SEARCH("/checkout/api/v1/order/advancedSearch"),
    CHECKOUT_ORDER("/checkout/api/v1/Order/#{orderId}"),
    PRODUCT_ORDER_PRODUCT_INSTANCES("/product/api/v1/Order/#{orderId}/ProductInstanceWithMetaData"),
    PRODUCT_TERMINAL_DATA_SET_SEARCH("/product/api/v1/TerminalDataSet/advancedSearch"),
    PRODUCT_TERMINAL_DATA_SET_IMPORT_AWS("/product/api/v1/TerminalDataSet?import=true"),
    PRODUCT_INSTANCE("/product/api/v1/productInstance/#{productInstanceId}"),
    /**
     * Create a child product instance (e.g. add a payment method under a parent product).
     */
    PRODUCT_INSTANCE_ADD("/product/api/v1/productInstance"),

    // ── Merchant Portal ──────────────────────────────────────────
    /**
     * CMS fragments for portal shell (layout, nav) — onboarding API host.
     */
    CMS_CONTENT_LAYOUT("/merchantexperienceonboarding/api/v1/Cms/content/layout"),
    CMS_CONTENT_PAYMENT_LINKS("/merchantexperienceonboarding/api/v1/Cms/content/payment-links"),
    CMS_CONTENT_ACCOUNT_MANAGEMENT("/merchantexperienceonboarding/api/v1/Cms/content/account-management"),
    CMS_CONTENT_MSR("/merchantexperienceonboarding/api/v1/Cms/content/msr"),
    CMS_CONTENT_VAT("/merchantexperienceonboarding/api/v1/Cms/content/vat"),
    CMS_CONTENT_STORES("/merchantexperienceonboarding/api/v1/Cms/content/stores"),
    /**
     * Reports NS monthly statement detailed — see {@code BaseURI.PORTAL_PREPROD} {@code /aws} prefix.
     */
    REPORTS_MONTHLY_STATEMENT_DETAILED("/reports-ns/api/v1/reports/monthly-statement-detailed"),
    /**
     * Reports NS advanced search (v2) — see environments using {@code BaseURI.PORTAL_PREPROD} {@code /aws} prefix.
     */
    REPORTS_ADVANCED_SEARCH("/reports-ns/api/v2/reports/advancedSearch"),
    TOTAL_TRANSACTIONS_ANALYTICS_DATA("/reports/api/uae/PgwAnalytics/TotalTransactionsAnalyticsData"),
    PBL_ANALYTICS_DATA("/reports/api/uae/PgwAnalytics/PBLsAnalyticsData"),
    CURRENT_USER("/portalae-ns/api/v1/users/current-user"),
    USER_ENTITIES("/merchant-portal-ns/api/v2/user/#{userId}/entities"),
    TRANSACTIONS("/portalae-ns/api/v2/transactions"),
    TRANSACTIONS_PDF("/merchant-live-data/api/v3/transactions/pdf"),
    GENERATE_TRANSACTION_REPORT("/portalae-ns/api/v2/transactions/GenerateTransactionReport"),
    GET_REQUESTED_TRANSACTIONS("/portalae/api/v2/transactions/GetRequestedTransactions"),
    SHOP_PRODUCTS("/merchant-portal-ns/api/v1/shop-products"),
    SHOP_CART_ITEMS("/merchant-portal-ns/api/v1/cart/items"),
    SHOP_CART("/merchant-portal-ns/api/v1/cart"),
    SHOP_CART_ITEM_QUANTITY("/merchant-portal-ns/api/v1/cart/item-quantity"),
    SHOP_CART_ASSIGN_ITEM("/merchant-portal-ns/api/v1/cart/assign-item"),

    // ── GSDK / Old Stack Onboarding ──────────────────────────────
    GSDK_CONFIG_FEATURE_TOGGLES("/config/api/v1/portal/FeatureToggles"),
    GSDK_ONBOARDING_REFDATA("/onboarding/api/v1/refdata"),
    GSDK_PRODUCT_CATEGORIES("/product/api/v1/categories"),
    GSDK_PRODUCT_PRODUCTS("/product/api/v1/products"),
    GSDK_DUE_DILIGENCE_LEAD_VALIDATE("/due-diligence/api/v1/Lead/Validate"),
    GSDK_ONBOARDING_LEAD("/onboarding/api/v1/lead/#{leadId}"),
    GSDK_ONBOARDING_PHONE_VERIFY("/onboarding/api/v1/phone/verify"),
    GSDK_ONBOARDING_PHONE_CONFIRM("/onboarding/api/v1/phone/confirm/#{phoneLast6}"),
    GSDK_DUE_DILIGENCE_NAFATH_AUTH("/due-diligence/api/v1/nafath/auth"),
    GSDK_DUE_DILIGENCE_NAFATH_TOKEN("/due-diligence/api/v1/nafath/token"),
    GSDK_ONBOARDING_LEAD_DETAILS("/onboarding/api/v1/lead/#{leadId}/leadDetails"),
    GSDK_ONBOARDING_ACCOUNT("/onboarding/api/v1/account"),
    GSDK_PERMISSION_ROLES("/permission/api/v1/roles"),
    GSDK_MERCHANT_PORTAL_USER_INFO("/merchant-portal/api/v1/userInfo"),
    GSDK_ACCOUNT_STATE("/account/api/v1/accountState"),
    GSDK_ONBOARDING_LEAD_MERCHANT("/onboarding/api/v1/lead-merchant"),
    GSDK_CHECKOUT_CART("/checkout/api/v1/Cart"),
    GSDK_CHECKOUT_CART_CHECKOUT("/checkout/api/v1/Cart/checkout"),
    GSDK_CHECKOUT_CART_CREATE("/checkout/api/v1/Cart/create"),
    GSDK_CHECKOUT_CART_STORE_PRODUCT("/checkout/api/v1/Cart/store/product"),
    GSDK_MERCHANT_PORTAL_BUSINESS("/merchant-portal/api/v1/Merchant/business/#{merchantId}"),
    GSDK_MERCHANT_PORTAL_BANK_ACCOUNT("/merchant-portal/api/v1/BankAccount"),
    GSDK_MERCHANT_PORTAL_AUDITLOG("/merchant-portal/api/v1/Auditlog"),
    GSDK_MERCHANT_PORTAL_DOCUMENT("/merchant-portal/api/v1/Document"),
    GSDK_MERCHANT_PORTAL_STORE("/merchant-portal/api/v1/Store"),
    GSDK_MERCHANT_PORTAL_STORE_BY_MERCHANT("/merchant-portal/api/v1/Store/#{merchantId}"),
    GSDK_MERCHANT_PORTAL_NEW_BUSINESS("/merchant-portal/api/v1/Merchant/business"),
    GSDK_CONTRACT_VERIFY("/contract/api/v1/Contract/verify"),
    GSDK_CONTRACT_CONFIRM("/contract/api/v1/Contract/confirm"),
    GSDK_BACKOFFICE_SHAREHOLDER_INDIVIDUAL("/backoffice/api/v1/Shareholder/individual"),
    GSDK_BACKOFFICE_SHAREHOLDER_COMPANY("/backoffice/api/v1/Shareholder/shareholdercompanies/create"),
    GSDK_BACKOFFICE_MERCHANT_COMMENT("/backoffice/api/v1/merchant/#{merchantId}/comment"),
    GSDK_PRODUCT_ORDER_INSTANCES("/product/api/v1/Order/#{orderId}/ProductInstances"),
    GSDK_PRODUCT_TERMINAL_DATA_SET("/product/api/v1/TerminalDataSet"),
    GSDK_PRODUCT_REGISTER_TABBY("/product/api/v1/productInstance/registerTabby"),
    GSDK_FEDERATION_CONTRACT_LIST("/gsdk-federation/api/v1/merchant-contract-list"),
    GSDK_FEDERATION_CONTRACT("/gsdk-federation/api/v1/merchant-contract"),
    GSDK_FEDERATION_CONTRACT_MAPPINGS("/gsdk-federation/api/v1/contract-mappings"),
    GSDK_FEDERATION_CONTRACT_REASSIGN("/gsdk-federation/api/v1/contract-mappings/reassign"),
    GSDK_ONBOARDING_HEALTH("/onboarding/api/health"),

    //paymentGateway Login
    PGW_PORTAL_USER_LOGIN("/pgwauth/api/v1/User/Login"),


    // ── GSDK Configuration (www portal host — use absolute URL) ──
    GSDK_PORTAL_EXT_USERS_VIEW("/portal/api/v1/ext-users/view"),
    GSDK_PORTAL_EXT_USERS_ORGS("/portal/api/v1/ext-users/#{gsdkUserId}/organizations/view"),
    GSDK_PORTAL_MY_ROLE("/portal/api/v1/my/role"),
    GSDK_PORTAL_FINANCIER_CONTRACT("/portal/api/v1/management/financier/contract"),
    GSDK_PORTAL_VALIDATE_REFERRER_RATE("/portal/api/v1/validation/referrer-contracts/validate-by-rate"),
    GSDK_PORTAL_REFERRER_LINK("/portal/api/v1/management/referrer/link"),
    GSDK_PORTAL_FINANCIER_CAPS("/portal/api/v1/management/financier/caps"),
    GSDK_PORTAL_BANK_PAYOUT_SETTINGS("/portal/api/v1/profiles/additional/#{gsdkUserId}/bank-payout-settings"),
    GSDK_PORTAL_APPROVE_CHANGE_REQUEST(
            "/portal/api/v1/management/double-confirmation/change-requests/#{gsdkChangeRequestId}/approve"),
    GSDK_PORTAL_VERIFY_CHANGE_REQUEST(
            "/portal/api/v1/management/double-confirmation/change-requests/#{gsdkChangeRequestId}"),
    GSDK_PORTAL_CUSTOMER_PAYOUT_SETTINGS(
            "/portal/api/v1/management/customer-contracts/#{gsdkOrgId}/customer-payout-settings/view"),
    GSDK_PORTAL_ORGANIZATIONS("/portal/api/v1/organizations"),
    GSDK_PORTAL_BANK_TEMPLATE_TRANSFERS("/portal/api/v1/management/bank-templates/transfers-by-organizations"),
    GSDK_PORTAL_SUBSCRIPTIONS("/portal/api/v1/subscriptions"),
    GSDK_PORTAL_CUSTOMER_CONTRACT_VIEW("/portal/api/v1/management/customer-contracts/#{gsdkOrgId}/view"),
    GSDK_PORTAL_COLLECTION_SETTINGS(
            "/portal/api/v1/profiles/additional/#{gsdkUserId}/merchant/collection-settings");

    private final String value;

    KsaBasePath(String path) {
        if (path == null || !path.startsWith("/")) {
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
