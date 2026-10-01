package gatling.config.enums;

import gatling.config.enums.egypt.EgyptBaseUri;
import gatling.config.enums.ksa.KsaBaseUri;
import gatling.config.enums.uae.UaeBaseUri;

/**
 * Region entry point for hosts.
 * Use BaseURI.ksa.PORTAL_PREPROD.
 */
public final class BaseURI {
    private BaseURI() {}

    public static final class KSA {
        private KSA() {}
        public static final KsaBaseUri PORTAL_TEST = KsaBaseUri.PORTAL_TEST;
        public static final KsaBaseUri PORTAL_PREPROD = KsaBaseUri.PORTAL_PREPROD;
        public static final KsaBaseUri PORTAL_PROD = KsaBaseUri.PORTAL_PROD;
        public static final KsaBaseUri APEX_TEST = KsaBaseUri.APEX_TEST;
        public static final KsaBaseUri APEX_PREPROD = KsaBaseUri.APEX_PREPROD;
        public static final KsaBaseUri APEX_PROD = KsaBaseUri.APEX_PROD;
        public static final KsaBaseUri API_APEX_TEST = KsaBaseUri.API_APEX_TEST;
        public static final KsaBaseUri API_APEX_PREPROD = KsaBaseUri.API_APEX_PREPROD;
        public static final KsaBaseUri API_APEX_PROD = KsaBaseUri.API_APEX_PROD;
        public static final KsaBaseUri PGW_TEST = KsaBaseUri.PGW_TEST;
        public static final KsaBaseUri PGW_PREPROD = KsaBaseUri.PGW_PREPROD;
        public static final KsaBaseUri PGW_PROD = KsaBaseUri.PGW_PROD;
        public static final KsaBaseUri GSDK_API_TEST = KsaBaseUri.GSDK_API_TEST;
        public static final KsaBaseUri GSDK_API_PREPROD = KsaBaseUri.GSDK_API_PREPROD;
        public static final KsaBaseUri GSDK_API_PROD = KsaBaseUri.GSDK_API_PROD;
    }

    public static final class UAE {
        private UAE() {}
        public static final UaeBaseUri PORTAL_TEST = UaeBaseUri.PORTAL_TEST;
        public static final UaeBaseUri PORTAL_PREPROD = UaeBaseUri.PORTAL_PREPROD;
        public static final UaeBaseUri PORTAL_PROD = UaeBaseUri.PORTAL_PROD;
        public static final UaeBaseUri APEX_TEST = UaeBaseUri.APEX_TEST;
        public static final UaeBaseUri APEX_PREPROD = UaeBaseUri.APEX_PREPROD;
        public static final UaeBaseUri APEX_PROD = UaeBaseUri.APEX_PROD;
        public static final UaeBaseUri API_APEX_TEST = UaeBaseUri.API_APEX_TEST;
        public static final UaeBaseUri API_APEX_PREPROD = UaeBaseUri.API_APEX_PREPROD;
        public static final UaeBaseUri API_APEX_PROD = UaeBaseUri.API_APEX_PROD;
        public static final UaeBaseUri PGW_TEST = UaeBaseUri.PGW_TEST;
        public static final UaeBaseUri PGW_PREPROD = UaeBaseUri.PGW_PREPROD;
        public static final UaeBaseUri PGW_PROD = UaeBaseUri.PGW_PROD;
        public static final UaeBaseUri GSDK_API_TEST = UaeBaseUri.GSDK_API_TEST;
        public static final UaeBaseUri GSDK_API_PREPROD = UaeBaseUri.GSDK_API_PREPROD;
        public static final UaeBaseUri GSDK_API_PROD = UaeBaseUri.GSDK_API_PROD;
    }

    public static final class EG {
        private EG() {}
        public static final EgyptBaseUri PORTAL_TEST = EgyptBaseUri.PORTAL_TEST;
        public static final EgyptBaseUri PORTAL_PREPROD = EgyptBaseUri.PORTAL_PREPROD;
        public static final EgyptBaseUri PORTAL_PROD = EgyptBaseUri.PORTAL_PROD;
        public static final EgyptBaseUri APEX_TEST = EgyptBaseUri.APEX_TEST;
        public static final EgyptBaseUri APEX_PREPROD = EgyptBaseUri.APEX_PREPROD;
        public static final EgyptBaseUri APEX_PROD = EgyptBaseUri.APEX_PROD;
        public static final EgyptBaseUri API_APEX_TEST = EgyptBaseUri.API_APEX_TEST;
        public static final EgyptBaseUri API_APEX_PREPROD = EgyptBaseUri.API_APEX_PREPROD;
        public static final EgyptBaseUri API_APEX_PROD = EgyptBaseUri.API_APEX_PROD;
        public static final EgyptBaseUri PGW_TEST = EgyptBaseUri.PGW_TEST;
        public static final EgyptBaseUri PGW_PREPROD = EgyptBaseUri.PGW_PREPROD;
        public static final EgyptBaseUri PGW_PROD = EgyptBaseUri.PGW_PROD;
        public static final EgyptBaseUri GSDK_API_TEST = EgyptBaseUri.GSDK_API_TEST;
        public static final EgyptBaseUri GSDK_API_PREPROD = EgyptBaseUri.GSDK_API_PREPROD;
        public static final EgyptBaseUri GSDK_API_PROD = EgyptBaseUri.GSDK_API_PROD;
    }
}
