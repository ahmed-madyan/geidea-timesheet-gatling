package gatling.config.region;

import gatling.config.env.EnvConfig;

/**
 * Region-specific configuration for multi-region performance testing.
 *
 * <p>Contains all parameters that vary between regions (e.g. KSA, UAE, Egypt),
 * such as country dial prefix, Keycloak realm, client ID, and feeder file.
 *
 * <p>Usage:
 * <pre>{@code
 * RegionConfig region = RegionConfig.KSA;
 * String prefix = region.countryPrefix();  // "+966"
 * }</pre>
 *
 * <p>The active region is resolved by the {@code region} system property
 * (default: {@code ksa}).
 */
public record RegionConfig(
        String code,
        String country,
        String countryPrefix,
        String currency,
        String realm,
        String clientId,
        String portalMerchantsFeederFile,
        String multitenantMerchantsFeederFile,
        String cpFeederFile,
        String cnpFeederFile,
        String defaultCounterparty,
        String defaultAppLanguage

) {

    // =========================================================================
    // Predefined Region Configurations
    // =========================================================================

    /**
     * Saudi Arabia — dial-prefix +966, default realm "test", client "portalme".
     *
     * <p>Merchant CSV paths follow {@code data/{region}/{env}/merchants/{cp|cnp}/merchants.csv}
     * (classpath-relative, under {@code src/test/resources}).
     *
     * <p>Note: the feeder path segment is built from {@code EnvConfig.active().name()} directly
     * rather than {@code active().realm()}. The latter would dereference {@link #active()},
     * which itself returns {@code this} constant — and during {@code <clinit>} that field
     * is still {@code null}, causing a circular-initialisation NPE that poisons the class
     * for the rest of the JVM. Using {@code EnvConfig.active().name()} twice keeps the
     * semantics identical (the realm on this region is, by construction, the active env's
     * name) while breaking the cycle.
     */
    public static final RegionConfig KSA = new RegionConfig(
            "ksa",
            "SAU",
            "+966",
            "SAR",
            EnvConfig.active().name(),
            "portalme",
            "data/ksa/" + EnvConfig.active().name() + "/portal/merchants/merchants.csv",
            "data/ksa/" + EnvConfig.active().name() + "/multitenant/merchants/merchants.csv",
            "data/ksa/" + EnvConfig.active().name() + "/portal/merchants/byChannel/cp/merchants.csv",
            "data/ksa/" + EnvConfig.active().name() + "/portal/merchants/byChannel/cnp/merchants.csv",
            "GEIDEA_SAUDI",
            "en-US"
    );

    /** data/ksa/preprod/portal/merchants/byChannel/cnp/merchants.csv
     * United Arab Emirates — dial-prefix +971, default realm "test", client "portalme".
     */
    public static final RegionConfig UAE = new RegionConfig(
            "uae",
            "uae",
            "+971", EnvConfig.active().name(),
            "AED",
            "portalme",
            "data/uae/" + EnvConfig.active().name() + "/portal/merchants/merchants.csv",
            "data/uae/" + EnvConfig.active().name() + "/multitenant/merchants/merchants.csv",
            "data/uae/" + EnvConfig.active().name() + "/portal/merchants/cp/merchants.csv",
            "data/uae/" + EnvConfig.active().name() + "/portal/merchants/cnp/merchants.csv",
            "GEIDEA_SAUDI",
            "en-US"
    );

    /**
     * Egypt — dial-prefix +20, currency EGP, ISO country {@code EGY}.
     *
     * <p>Feeder paths follow the same layout as KSA:
     * {@code data/egypt/{env}/…}. Commit CSVs there before a run, or override
     * with {@code -Dfeeder}.
     *
     * <p>{@code defaultCounterparty} is {@code GEIDEA_EGYPT}. Confirm the live
     * counterparty code before a real Egypt run.
     */
    public static final RegionConfig EGYPT = new RegionConfig(
            "egypt",
            "EGY",
            "+20",
            "EGP",
            EnvConfig.active().name(),
            "portalme",
            "data/egypt/" + EnvConfig.active().name() + "/portal/merchants/merchants.csv",
            "data/egypt/" + EnvConfig.active().name() + "/multitenant/merchants/merchants.csv",
            "data/egypt/" + EnvConfig.active().name() + "/portal/merchants/byChannel/cp/merchants.csv",
            "data/egypt/" + EnvConfig.active().name() + "/portal/merchants/byChannel/cnp/merchants.csv",
            "GEIDEA_EGYPT",
            "en-US"
    );
    // =========================================================================
    // Active Region Resolution
    // =========================================================================

    /**
     * Returns the region identified by the {@code region} system property.
     *
     * <p>Supported values (case-insensitive): {@code ksa}, {@code uae}, {@code egypt}.
     * Defaults to {@link #KSA} when the property is absent or unrecognised.
     *
     * @return the resolved {@link RegionConfig}
     */
    public static RegionConfig active() {
        String prop = System.getProperty("region", "ksa").toLowerCase();
        return switch (prop) {
            case "uae" -> UAE;
            case "egypt" -> EGYPT;
            default -> KSA;
        };
    }

    /**
     * Overrides the default record accessor to allow dynamic terminal input.
     * Use {@code -Dfeeder=data/Custom.csv} to override the default regional value.
     */
    @Override
    public String cpFeederFile() {
        return System.getProperty("feeder", this.cpFeederFile);
    }


    /**
     * Overrides the default record accessor to allow dynamic terminal input.
     * Use {@code -Dfeeder=data/Custom.csv} to override the default regional value.
     */
    @Override
    public String cnpFeederFile() {
        return System.getProperty("feeder", this.cnpFeederFile);
    }

    /**
     * Overrides the default record accessor to allow dynamic terminal input.
     * Use {@code -Dfeeder=data/Custom.csv} to override the default regional value.
     */
    @Override
    public String portalMerchantsFeederFile() {
        return System.getProperty("feeder", this.portalMerchantsFeederFile);
    }

    @Override
    public String multitenantMerchantsFeederFile() {
        return System.getProperty("feeder", this.portalMerchantsFeederFile);
    }
}
