package gatling.utils;

import gatling.config.env.EnvConfig;
import gatling.config.region.RegionConfig;
import io.gatling.javaapi.core.ChainBuilder;
import io.gatling.javaapi.core.FeederBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static io.gatling.javaapi.core.CoreDsl.exec;

/**
 * Utility class for generating random data for Merchant Onboarding and
 * Transaction scenarios.
 */
public final class DataGenerator {

    private DataGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Default inclusive lower bound for {@link #generateRandomAmount()} (major units).
     * Override with {@code -DcreateTxnAmountMin}.
     */
    public static final int DEFAULT_AMOUNT_MIN = 100;

    /**
     * Default inclusive upper bound for {@link #generateRandomAmount()} (major units).
     * Override with {@code -DcreateTxnAmountMax}.
     */
    public static final int DEFAULT_AMOUNT_MAX = 99_999;

    /**
     * Generates a unique inter-system transaction ID using current millis and a random part.
     *
     * @return a unique inter-system ID string in format {@code "millis:random"}
     */
    public static String generateInterSystemId() {
        long part1 = System.currentTimeMillis();
        long part2 = ThreadLocalRandom.current().nextLong(1_000_000_000_000L);
        return part1 + ":" + part2;
    }

    /**
     * Generates a random transaction amount as a major-unit string with exactly two
     * fraction digits (e.g. {@code "150.00"}).
     *
     * <p>Inclusive integer bounds come from system properties
     * {@code createTxnAmountMin} / {@code createTxnAmountMax}
     * (defaults {@value #DEFAULT_AMOUNT_MIN}–{@value #DEFAULT_AMOUNT_MAX}).
     *
     * @return amount formatted as {@code #.00} using {@link Locale#US}
     */
    public static String generateRandomAmount() {
        int amountMin = Integer.parseInt(
                System.getProperty("createTxnAmountMin", Integer.toString(DEFAULT_AMOUNT_MIN)));
        int amountMax = Integer.parseInt(
                System.getProperty("createTxnAmountMax", Integer.toString(DEFAULT_AMOUNT_MAX)));
        if (amountMax < amountMin) {
            throw new IllegalStateException(
                    "createTxnAmountMax (" + amountMax + ") must be >= createTxnAmountMin ("
                            + amountMin + ")");
        }
        int whole = ThreadLocalRandom.current().nextInt(amountMin, amountMax + 1);
        return String.format(Locale.US, "%.2f", (double) whole);
    }

    /**
     * Returns a Gatling {@link ChainBuilder} that sets the {@code interSystemID}
     * session variable using {@link #generateInterSystemId()}.
     *
     * @return chain that populates {@code interSystemID} in the Gatling session
     */
    public static ChainBuilder setInterSystemId() {
        return exec(session -> session.set("interSystemID", generateInterSystemId()));
    }

    /**
     * Generates a random National ID: starts with "61" + 8 random digits.
     *
     * @return random National ID string
     */
    public static String generateNationalId() {
        long randomPart = ThreadLocalRandom.current().nextLong(10000000L, 100000000L); // 8 digits
        return "61" + randomPart;
    }

    /**
     * First-name pool used by . Common test-friendly names
     * suitable for synthetic merchant / contact data.
     */
    private static final String[] FIRST_NAMES = {
            "Ahmed", "Sara", "Omar", "Lina", "Khalid", "Mona",
            "Yousef", "Fatima", "Hassan", "Layla", "Ali", "Noura",
            "Tariq", "Hala", "Bilal", "Maya", "Karim", "Reem",
            "Faisal", "Dana", "Ibrahim", "Salma", "Nasser", "Huda"
    };

    /**
     * Last-name pool used by  when building a full name.
     */
    private static final String[] LAST_NAMES = {
            "Al-Saud", "Al-Qahtani", "Al-Ghamdi", "Al-Harbi", "Al-Otaibi",
            "Al-Zahrani", "Al-Mutairi", "Al-Shehri", "Al-Dosari", "Al-Anazi",
            "Mante", "Salem", "Rashid", "Hadi", "Nasser", "Farah", "Jaber"
    };

    /**
     * Generates a random first name picked from {@link #FIRST_NAMES} and suffixed with
     * 4 random digits to guarantee uniqueness across iterations.
     *
     * @return random first name string in the form {@code "First NNNN"}
     */
    public static String generateRandomFirstName() {
        String first = FIRST_NAMES[ThreadLocalRandom.current().nextInt(FIRST_NAMES.length)];
        return first + " " + randomDigits(4);
    }

    /**
     * Generates a random last name picked from {@link #LAST_NAMES} and suffixed with
     * 4 random digits to guarantee uniqueness across iterations.
     *
     * @return random last name string in the form {@code "Last NNNN"}
     */
    public static String generateRandomLastName() {
        String last = LAST_NAMES[ThreadLocalRandom.current().nextInt(LAST_NAMES.length)];
        return last + " " + randomDigits(4);
    }

    /**
     * Returns a Gatling {@link ChainBuilder} that sets the {@code randomFirstName} and
     * {@code randomLastName} session variables via {@link #generateRandomFirstName()} and
     * {@link #generateRandomLastName()}. {@code exec} this immediately before any request
     * that interpolates {@code #{randomFirstName}} / {@code #{randomLastName}} so every
     * iteration gets fresh values.
     *
     * @return chain that populates {@code randomFirstName} and {@code randomLastName}
     */
    public static ChainBuilder setRandomName() {
        return exec(session -> session
                .set("randomFirstName", generateRandomFirstName())
                .set("randomLastName", generateRandomLastName()));
    }

    /**
     * Format used by {@link #generateRandomDateOfBirth()} — 8-digit {@code yyyyMMdd},
     * matching the {@code dateOfBirth} field shape in the Merchant-Onboarding payloads
     * (e.g. {@code "19880207"}).
     */
    private static final DateTimeFormatter DATE_OF_BIRTH_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Minimum age (in years) for {@link #generateRandomDateOfBirth()}; the latest valid
     * birth date is {@code today - MIN_AGE_YEARS}.
     */
    private static final int MIN_AGE_YEARS = 18;

    /**
     * Maximum age (in years) for {@link #generateRandomDateOfBirth()}; the earliest valid
     * birth date is {@code today - MAX_AGE_YEARS}.
     */
    private static final int MAX_AGE_YEARS = 80;

    /**
     * Generates a random {@code dateOfBirth} as an 8-digit {@code yyyyMMdd} string
     * (e.g. {@code "19880207"}). The chosen date is uniformly distributed between
     * {@code today - MAX_AGE_YEARS} (inclusive) and {@code today - MIN_AGE_YEARS}
     * (inclusive), so the implied age always falls in {@code [MIN_AGE_YEARS, MAX_AGE_YEARS]}.
     *
     * @return random birth date formatted as {@code yyyyMMdd}
     */
    public static String generateRandomDateOfBirth() {
        LocalDate today = LocalDate.now();
        long latestEpoch = today.minusYears(MIN_AGE_YEARS).toEpochDay();
        long earliestEpoch = today.minusYears(MAX_AGE_YEARS).toEpochDay();
        long randomEpoch = ThreadLocalRandom.current().nextLong(earliestEpoch, latestEpoch + 1);
        return LocalDate.ofEpochDay(randomEpoch).format(DATE_OF_BIRTH_FMT);
    }

    /**
     * Returns a Gatling {@link ChainBuilder} that sets the {@code randomDateOfBirth}
     * session variable using {@link #generateRandomDateOfBirth()}. {@code exec} this
     * immediately before any request that interpolates {@code #{randomDateOfBirth}} in
     * its body template so every iteration gets a fresh value.
     *
     * @return chain that populates {@code randomDateOfBirth} in the Gatling session
     */
    public static ChainBuilder setRandomDateOfBirth() {
        return exec(session -> session.set("randomDateOfBirth", generateRandomDateOfBirth()));
    }

    /**
     * Suffix appended to an {@code yyyy-MM-dd} date to form a start-of-day UTC ISO-8601 timestamp
     * with millisecond precision, e.g. {@code 1988-02-07T00:00:00.000Z}.
     */
    private static final String START_OF_DAY_UTC_SUFFIX = "T00:00:00.000Z";

    /**
     * Validity window (years from today) used by {@link #generateIdExpiryIso()}.
     */
    private static final int ID_EXPIRY_YEARS = 5;

    /**
     * Generates a random date of birth as a start-of-day UTC ISO-8601 timestamp
     * (e.g. {@code 1988-02-07T00:00:00.000Z}). Same age distribution as
     * {@link #generateRandomDateOfBirth()}, formatted for date-time API fields.
     *
     * @return random birth date as an ISO-8601 UTC timestamp
     */
    public static String generateRandomDobIso() {
        LocalDate dob = LocalDate.parse(generateRandomDateOfBirth(), DATE_OF_BIRTH_FMT);
        return isoStartOfDayUtc(dob);
    }

    /**
     * Generates an ID expiry date {@value #ID_EXPIRY_YEARS} years from today as a start-of-day
     * UTC ISO-8601 timestamp (e.g. {@code 2031-06-28T00:00:00.000Z}).
     *
     * @return future ID expiry date as an ISO-8601 UTC timestamp
     */
    public static String generateIdExpiryIso() {
        return isoStartOfDayUtc(LocalDate.now().plusYears(ID_EXPIRY_YEARS));
    }

    private static String isoStartOfDayUtc(LocalDate date) {
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE) + START_OF_DAY_UTC_SUFFIX;
    }

    /**
     * Saudi mobile prefixes (9-digit local format, leading {@code 5x}): STC, Mobily, Zain KSA, Salam Mobile.
     */
    private static final String[] KSA_MOBILE_PREFIXES = {
            "50", "53", "55", // STC
            "54", "56", "57", // Mobily
            "58", "59", // Zain KSA
            "51" // Salam Mobile
    };

    /**
     * Generates a random phone number: a random KSA mobile prefix + 7 random digits.
     *
     * @return random phone number string (9 digits)
     */
    public static String generatePhoneNumber() {
        String prefix = KSA_MOBILE_PREFIXES[ThreadLocalRandom.current().nextInt(KSA_MOBILE_PREFIXES.length)];
        long randomPart = ThreadLocalRandom.current().nextLong(1000000L, 10000000L); // 7 digits
        return prefix + randomPart;
    }

    /**
     * Generates a random Registration Number: "FL-" + 8 random digits.
     *
     * @return random Registration Number string
     */
    public static String generateRegistrationNumber() {
        long randomPart = ThreadLocalRandom.current().nextLong(10000000L, 100000000L); // 8 digits
        return "FL-" + randomPart;
    }

    /**
     * Maximum length accepted by Apex Merchant-Onboarding for
     * {@code merchant.contact.emailAddress} and {@code merchant.address.emailAddress}
     * (server-side validation: {@code size must be between 0 and 64}).
     */
    private static final int EMAIL_MAX_LENGTH = 64;

    /**
     * Generates a random email address using the automation prefix
     * {@code perf.{env}.{region}.}, a 12-hex-char fragment from a UUID, and an
     * 8-digit millisecond suffix — yielding a compact, collision-resistant address that
     * fits within the {@value #EMAIL_MAX_LENGTH}-char limit enforced by the
     * Merchant-Onboarding API (e.g. {@code perf.preprod.ksa.7d784971abcd12345678@geidea.net},
     * 48 chars).
     *
     * <p>Local-part length: {@code 5 + env.length() + 1 + region.length() + 1 + 12 + 8 = 27 + env + region}.
     * For typical envs/regions this stays well under 64; if env or region names ever push
     * the address past the limit, the random portion is truncated to keep the result valid.
     *
     * @return random email string of length {@code <=} {@value #EMAIL_MAX_LENGTH}
     */
    public static String generateRandomEmail() {
        String uuidHex = UUID.randomUUID().toString().replace("-", "").substring(0, 2);
        String millisSuffix = String.format("%03d", System.currentTimeMillis() % 100_000_000L);
        String local = "perf." + EnvConfig.active().name() + "." + RegionConfig.active().code()
                + "." + uuidHex + millisSuffix;
        String domain = "@geidea.net";
        String email = local + domain;
        if (email.length() > EMAIL_MAX_LENGTH) {
            int overflow = email.length() - EMAIL_MAX_LENGTH;
            local = local.substring(0, Math.max(0, local.length() - overflow));
            email = local + domain;
        }
        return email;
    }

    /**
     * Given a phone number, returns the last 6 digits as an OTP key.
     *
     * @param phoneNumber The phone number string
     * @return last 6 digits
     */
    public static String getOtpKey(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 6) {
            return phoneNumber;
        }
        return phoneNumber.substring(phoneNumber.length() - 6);
    }

    // =========================================================================
    // Terminal Data Set (TDS) — TID / MID / TRSM
    // =========================================================================

    /**
     * Uppercase Latin alphabet used by {@link #randomChars(int)}.
     */
    private static final String UPPERCASE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     * Generates a string of {@code n} random decimal digits (0–9).
     *
     * @param n the number of digits to generate
     * @return string of length {@code n} containing only ASCII digits
     */
    public static String randomDigits(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }

    /**
     * Generates a string of {@code n} random decimal digits whose first digit is 1–9
     * (no leading zero). Remaining digits may be 0–9.
     *
     * @param n the number of digits to generate
     * @return string of length {@code n} containing only ASCII digits, not starting with {@code 0}
     */
    public static String randomDigitsNoLeadingZero(int n) {
        StringBuilder sb = new StringBuilder(n);
        sb.append(ThreadLocalRandom.current().nextInt(1, 10));
        for (int i = 1; i < n; i++) {
            sb.append(ThreadLocalRandom.current().nextInt(10));
        }
        return sb.toString();
    }

    /**
     * Generates a string of {@code n} random uppercase ASCII letters.
     *
     * @param n the number of characters to generate
     * @return string of length {@code n} containing only A–Z characters
     */
    public static String randomChars(int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(UPPERCASE_ALPHABET.charAt(ThreadLocalRandom.current().nextInt(UPPERCASE_ALPHABET.length())));
        }
        return sb.toString();
    }

    /**
     * Default acquirer prefix used by both TID and MID maps when no acquirer
     * (or an unmapped acquirer) is supplied.
     */
    private static final String DEFAULT_ACQUIRER_PREFIX = "69";

    /**
     * Returns the 2-digit TID prefix for the given acquirer, mirroring the
     * Postman pre-request {@code tidPrefixMap}:
     * <ul>
     *   <li>{@code DEFAULT_BANK} → {@code "69"}</li>
     *   <li>{@code SABB_BANK} → {@code "90"}</li>
     *   <li>{@code SABB} → {@code "65"}</li>
     *   <li>{@code ARB} → {@code "69"}</li>
     *   <li>any other / null → {@code "69"}</li>
     * </ul>
     *
     * @param acquirerBank the acquirer code (Gatling session {@code AcquirerBank})
     * @return 2-digit TID prefix
     */
    public static String tdsTidPrefix(String acquirerBank) {
        if (acquirerBank == null) {
            return DEFAULT_ACQUIRER_PREFIX;
        }
        return switch (acquirerBank) {
            case "DEFAULT_BANK" -> "69";
            case "SABB_BANK" -> "90";
            case "SABB" -> "65";
            case "ARB" -> "69";
            default -> DEFAULT_ACQUIRER_PREFIX;
        };
    }

    /**
     * Returns the 2-digit MID prefix for the given acquirer, mirroring the
     * Postman pre-request {@code midPrefixMap}:
     * <ul>
     *   <li>{@code DEFAULT_BANK} → {@code "69"}</li>
     *   <li>{@code SABB_BANK} → {@code "64"}</li>
     *   <li>{@code SABB} → {@code "64"}</li>
     *   <li>{@code ARB} → {@code "69"}</li>
     *   <li>any other / null → {@code "69"}</li>
     * </ul>
     *
     * @param acquirerBank the acquirer code (Gatling session {@code AcquirerBank})
     * @return 2-digit MID prefix
     */
    public static String tdsMidPrefix(String acquirerBank) {
        if (acquirerBank == null) {
            return DEFAULT_ACQUIRER_PREFIX;
        }
        return switch (acquirerBank) {
            case "DEFAULT_BANK" -> "69";
            case "SABB_BANK" -> "64";
            case "SABB" -> "64";
            case "ARB" -> "69";
            default -> DEFAULT_ACQUIRER_PREFIX;
        };
    }

    /**
     * Generates a TDS Terminal ID: acquirer-derived 2-digit prefix + 6 random
     * digits (8 digits total). See {@link #tdsTidPrefix(String)} for the
     * acquirer → prefix mapping.
     *
     * @param acquirerBank the acquirer code (Gatling session {@code AcquirerBank})
     * @return random TDS TID
     */
    public static String generateTdsTid(String acquirerBank) {
        return tdsTidPrefix(acquirerBank) + randomDigits(6);
    }

    /**
     * Builds a TDS Full Terminal ID by appending 8 random digits to the supplied
     * TID, producing a 16-digit string.
     *
     * @param tdsTid the base TID (typically from {@link #generateTdsTid(String)})
     * @return full TID (TID + 8 random digits)
     */
    public static String generateTdsFullTid(String tdsTid) {
        return tdsTid + randomDigits(8);
    }

    /**
     * Generates a TDS Merchant ID: acquirer-derived 2-digit prefix + 10 random
     * digits (12 digits total). See {@link #tdsMidPrefix(String)} for the
     * acquirer → prefix mapping.
     *
     * @param acquirerBank the acquirer code (Gatling session {@code AcquirerBank})
     * @return random TDS MID
     */
    public static String generateTdsMid(String acquirerBank) {
        return tdsMidPrefix(acquirerBank) + randomDigits(10);
    }

    /**
     * Generates a TDS TRSM code: 3 random uppercase letters + 3 random digits.
     *
     * @return random TDS TRSM
     */
    public static String generateTdsTrsm() {
        return randomChars(3) + randomDigits(3);
    }

    /**
     * Vendor ID assigned to the {@code GO_AIR} channel (product) per the Postman
     * pre-request script. All other channels use {@code null} for vendor ID.
     */
    private static final String GO_AIR_VENDOR_ID = "0fb49db4-9e9f-4675-02ed-08deadeeabae";

    /**
     * Resolves the vendor ID for the given product (channel type), mirroring the
     * Postman pre-request:
     * <pre>{@code
     * vendorId = (productCode === "GO_AIR") ? "0fb49db4-..." : null;
     * }</pre>
     *
     * @param productCode the chosen product / channel type (e.g. {@code GO_AIR})
     * @return vendor ID UUID for {@code GO_AIR}, otherwise {@code null}
     */
    public static String vendorIdFor(String productCode) {
        return "GO_AIR".equals(productCode) ? GO_AIR_VENDOR_ID : null;
    }

    /**
     * Returns a Gatling {@link ChainBuilder} that populates Terminal Data Set
     * variables in the session, mirroring the Postman pre-request script for
     * {@code POST /aws/product/api/v1/TerminalDataSet?import=true}.
     *
     * <p>Reads {@code AcquirerBank} and {@code chosenProductName} from the
     * session and writes:
     * <ul>
     *   <li>{@code tdsTid} — {@link #tdsTidPrefix(String)} + 6 random digits</li>
     *   <li>{@code tdsFullTid} — {@code tdsTid} + 8 random digits</li>
     *   <li>{@code tdsMid} — {@link #tdsMidPrefix(String)} + 10 random digits</li>
     *   <li>{@code tdsTrsm} — 3 random uppercase letters + 3 random digits</li>
     *   <li>{@code vendorId} — {@value #GO_AIR_VENDOR_ID} for {@code GO_AIR},
     *       otherwise {@code null}</li>
     *   <li>{@code vendorIdJson} — JSON-safe rendering of {@code vendorId}:
     *       the literal {@code null} when missing, otherwise the value quoted
     *       as a JSON string. Use this in body templates as
     *       {@code "vendorId": #{vendorIdJson}}.</li>
     * </ul>
     *
     * @return chain that populates TDS session variables
     */
    public static ChainBuilder setTerminalDataSetVariables() {
        return exec(session -> {
            String acquirerBank = session.getString("AcquirerBank");
            String productCode = session.getString("chosenProductName");

            String tdsTid = generateTdsTid(acquirerBank);
            String tdsFullTid = generateTdsFullTid(tdsTid);
            String tdsMid = generateTdsMid(acquirerBank);
            String tdsTrsm = generateTdsTrsm();
            String vendorId = vendorIdFor(productCode);
            String vendorIdJson = (vendorId == null) ? "null"
                    : "\"" + vendorId.replace("\"", "\\\"") + "\"";

            return session
                    .set("tdsTid", tdsTid)
                    .set("tdsFullTid", tdsFullTid)
                    .set("tdsMid", tdsMid)
                    .set("tdsTrsm", tdsTrsm)
                    .set("vendorId", vendorId)
                    .set("vendorIdJson", vendorIdJson);
        });
    }

    // =========================================================================
    // CNP (Card-Not-Present) Product Configuration
    // =========================================================================

    /**
     * Generates a random API password as a UUID v4 in canonical string form:
     * {@code xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx} with lowercase hex digits,
     * e.g. {@code 32b02c02-ce1a-4f6b-9ffb-91bb769b4137}. Mirrors the Postman
     * pre-request {@code pm.collectionVariables.set("APIpass", uuid.v4())}.
     *
     * @return lowercase RFC-4122 string representation of a random UUID v4
     */
    public static String generateApiPass() {
        return UUID.randomUUID().toString().toLowerCase(Locale.ROOT);
    }

    // =========================================================================
    // Add Payment Method — validFrom timestamp
    // =========================================================================

    /**
     * Returns the current UTC instant formatted as an ISO-8601 string with
     * millisecond precision and a trailing {@code Z}, e.g.
     * {@code 2026-05-11T19:40:27.910Z}.
     *
     * <p>Matches the portal's {@code validFrom} payload format used by
     * {@code POST /aws/product/api/v1/productInstance} (Add Payment Method).
     *
     * @return ISO-8601 UTC timestamp at millisecond precision
     */
    public static String currentValidFromIsoMillis() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS).toString();
    }

    /**
     * Returns a Gatling {@link ChainBuilder} that sets the
     * {@code currentMethodValidFrom} session variable to the current UTC
     * instant in ISO-8601 millisecond format (e.g. {@code 2026-05-11T19:40:27.910Z}).
     *
     * <p>Rendered into {@code bodies/merchant_configuration/add_payment_method.json}
     * as the {@code validFrom} field. Must be {@code exec}'d immediately
     * before each Add Payment Method request so every payment method gets a
     * fresh, monotonically increasing timestamp.
     *
     * @return chain that populates {@code currentMethodValidFrom}
     */
    public static ChainBuilder setCurrentMethodValidFrom() {
        return exec(session -> session.set("currentMethodValidFrom", currentValidFromIsoMillis()));
    }

    // =========================================================================
    // Product Name
    // =========================================================================

    /**
     * Returns the path to the product name CSV file.
     * Can be overridden via system property {@code -DproductNameFile=path/to/file.csv}.
     *
     * @return classpath-relative path to the product name CSV
     */
    public static String getProductNamePath() {
        String defaultPath = "data/" + RegionConfig.active().code() + "/" + EnvConfig.active().name() + "/portal/merchant_creation/onboarding/product_names.csv";
        return System.getProperty("productNameFile", defaultPath);
    }

    /**
     * Creates a circular CSV feeder for product names.
     * CSV must contain column: chosenProductName
     *
     * @return configured CSV feeder for product names
     */
    public static FeederBuilder<Object> productNameFeederByCode(String header, String value) {
        return FeederFactory.csvFile(getProductNamePath(), FeederFactory.FileStrategy.BY_CODE, header, value);
    }

    /**
     * Creates a random CSV feeder for product names.
     * CSV must contain column: chosenProductName
     *
     * @return configured CSV feeder for product names with random selection
     */
    public static FeederBuilder<String> productNameFeederCircular() {
        return FeederFactory.csvFile(getProductNamePath(), FeederFactory.FileStrategy.CIRCULAR);
    }
    // =========================================================================
    // Business Type (Code, Id, Name)
    // =========================================================================

    /**
     * Returns the path to the business type CSV file.
     * Can be overridden via system property {@code -DbusinessTypeFile=path/to/file.csv}.
     *
     * @return classpath-relative path to the business type CSV
     */
    public static String getBusinessTypePath() {
        String defaultPath = "data/" + RegionConfig.active().code() + "/" + EnvConfig.active().name() + "/portal/merchant_creation/onboarding/business_types.csv";
        return System.getProperty("businessTypeFile", defaultPath);
    }

    /**
     * Creates a circular CSV feeder for business type data.
     * CSV must contain columns: businessTypeCode, businessTypeId, businessTypeName
     *
     * @return configured CSV feeder for business type data
     */
    public static FeederBuilder<Object> businessTypeFeederByCode(String header, String value) {
        return FeederFactory.csvFile(getBusinessTypePath(), FeederFactory.FileStrategy.BY_CODE, header, value);
    }

    public static FeederBuilder<String> businessTypeFeederCircular() {
        return FeederFactory.csvFile(getBusinessTypePath(), FeederFactory.FileStrategy.CIRCULAR);
    }

    /**
     * Creates a random CSV feeder for business type data.
     * CSV must contain columns: businessTypeCode, businessTypeId, businessTypeName
     *
     * @return configured CSV feeder for business type data with random selection
     */
    public static FeederBuilder<String> businessTypeFeederRandom() {
        return FeederFactory.csvFile(getBusinessTypePath(), FeederFactory.FileStrategy.RANDOM);
    }

    // =========================================================================
    // MCC (Merchant Category Code)
    // =========================================================================

    /**
     * Returns the path to the MCC CSV file.
     * Can be overridden via system property {@code -DmccFile=path/to/file.csv}.
     *
     * @return classpath-relative path to the MCC CSV
     */
    public static String getMccPath() {
        String defaultPath = "data/" + RegionConfig.active().code() + "/" + EnvConfig.active().name() + "/portal/merchant_creation/onboarding/mcc_codes.csv";
        return System.getProperty("mccFile", defaultPath);
    }

    /**
     * Creates a circular CSV feeder for MCC data.
     * CSV must contain columns: MCC_code, MCC_id
     *
     * @return configured CSV feeder for MCC data
     */
    public static FeederBuilder<String> mccFeederCircular() {
        return FeederFactory.csvFile(getMccPath(), FeederFactory.FileStrategy.CIRCULAR);
    }

    public static FeederBuilder<Object> mccFeederCircularByCode(String header, String value) {
        return FeederFactory.csvFile(getMccPath(), FeederFactory.FileStrategy.CIRCULAR, header, value);
    }

    /**
     * Creates a random CSV feeder for MCC data.
     * CSV must contain columns: MCC_code, MCC_id
     *
     * @return configured CSV feeder for MCC data with random selection
     */
    public static FeederBuilder<String> mccFeederRandom() {
        return FeederFactory.csvFile(getMccPath(), FeederFactory.FileStrategy.RANDOM);
    }

    // =========================================================================
    // Acquirer bank (configuration)
    // =========================================================================

    /**
     * Returns the path to the acquirer bank CSV file.
     * Can be overridden via system property {@code -DaquirerBanksFile=path/to/file.csv}.
     *
     * @return classpath-relative path to {@code aquirerBanks.csv}
     */
    public static String getAcquirerBanksPath() {
        String defaultPath = "data/" + RegionConfig.active().code() + "/" + EnvConfig.active().name()
                + "/portal/merchant_creation/configuration/acquirer_dest_banks.csv";
        return System.getProperty("acquirer_dest_banks", defaultPath);
    }

    /**
     * Creates a circular CSV feeder for acquirer bank codes.
     * CSV must contain column: {@code AcquirerBank}
     *
     * @return configured CSV feeder for {@code AcquirerBank}
     */
    public static FeederBuilder<String> acquirerDestBankFeederCircular() {
        return FeederFactory.csvFile(getAcquirerBanksPath(), FeederFactory.FileStrategy.CIRCULAR);
    }

    public static FeederBuilder<Object> acquirerDestBankFeederByCode(String header, String value) {
        return FeederFactory.csvFile(getAcquirerBanksPath(), FeederFactory.FileStrategy.BY_CODE, header, value);
    }

    // =========================================================================
    // Payment Methods (configuration) — CNP Add Payment Method
    // =========================================================================

    /**
     * Returns the path to the payment methods CSV file.
     * Can be overridden via system property {@code -DpaymentMethodsFile=path/to/file.csv}.
     *
     * @return classpath-relative path to {@code payment_methods.csv}
     */
    public static String getPaymentMethodsPath() {
        String defaultPath = "data/" + RegionConfig.active().code() + "/" + EnvConfig.active().name()
                + "/portal/merchant_creation/configuration/payment_methods.csv";
        return System.getProperty("paymentMethodsFile", defaultPath);
    }

    /**
     * Creates a circular CSV feeder for payment method data.
     * CSV must contain columns: {@code paymentMethod}, {@code paymentMethodProductId}
     *
     * @return configured CSV feeder for payment method data
     */
    public static FeederBuilder<String> paymentMethodsFeederCircular() {
        return FeederFactory.csvFile(getPaymentMethodsPath(), FeederFactory.FileStrategy.CIRCULAR);
    }

    public static FeederBuilder<Object> paymentMethodsFeederByCode(String header, String value) {
        return FeederFactory.csvFile(getPaymentMethodsPath(), FeederFactory.FileStrategy.BY_CODE, header, value);
    }
    /**
     * Creates a random CSV feeder for payment method data.
     * CSV must contain columns: {@code paymentMethod}, {@code paymentMethodProductId}
     *
     * @return configured CSV feeder for payment method data with random selection
     */
    public static FeederBuilder<String> paymentMethodsFeederRandom() {
        return FeederFactory.csvFile(getPaymentMethodsPath(), FeederFactory.FileStrategy.RANDOM);
    }

    // =========================================================================
    // paymentGateway unit-test createTransaction — RRN / STAN / auth code / order ID
    // =========================================================================

    /**
     * Default paymentGateway terminal ID used by {@code create_pgw_transaction.json}.
     */
    public static final String PGW_UNIT_TEST_TERMINAL_ID = "RIYS2I03";

    /**
     * Default paymentGateway message type ({@code 220}) used when building {@link #buildPgwInterSystemId}.
     */
    public static final int PGW_UNIT_TEST_MESSAGE_TYPE = 220;

    /**
     * Generates a 12-digit paymentGateway retrieval reference number (RRN) part: {@code "61"} plus
     * 10 random decimal digits (e.g. {@code "614006042485"}).
     *
     * @return unique 12-digit RRN part string
     */
    public static String generateRrnPart() {
        return "61" + randomDigits(10);
    }

    /**
     * Generates a random 6-digit authorization code for paymentGateway create-transaction payloads.
     *
     * @return 6-digit authorization code string
     */
    public static String generateAuthorizationCode() {
        return randomDigits(6);
    }

    /**
     * Generates a random 5-digit STAN (systems trace audit number) for paymentGateway payloads.
     *
     * @return 5-digit STAN string
     */
    public static String generateStan() {
        return randomDigits(5);
    }

    /**
     * Generates the UUID suffix used in paymentGateway {@code orderId} ({@code "3" + suffix} in the body
     * template). Returns a lowercase hex UUID without hyphens.
     *
     * @return 32-char lowercase hex UUID fragment
     */
    public static String generatePgwOrderIdSuffix() {
        return UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
    }

    /**
     * Builds the paymentGateway {@code interSystemID} value:
     * {@code &lt;terminalId&gt;:&lt;zero-padded messageType&gt;:&lt;rrnPart&gt;}
     * (e.g. {@code "RIYS2I03:0220:614006042485"}).
     *
     * @param terminalId  terminal identifier (e.g. {@link #PGW_UNIT_TEST_TERMINAL_ID})
     * @param messageType ISO message type (e.g. {@code 220})
     * @param rrnPart     retrieval reference number part from {@link #generateRrnPart()}
     * @return formatted inter-system ID
     */
    public static String buildPgwInterSystemId(String terminalId, int messageType, String rrnPart) {
        return terminalId + ":" + String.format("%04d", messageType) + ":" + rrnPart;
    }

    /**
     * Returns a Gatling {@link ChainBuilder} that populates paymentGateway create-transaction session
     * variables consumed by {@code bodies/unit_test/create_pgw_transaction.json}:
     * {@code rrnPart}, {@code interSystemID}, {@code authorizationCode}, {@code stan},
     * and {@code orderId}.
     *
     * @return chain that populates paymentGateway transaction identifier session variables
     */
    public static ChainBuilder setPgwTransactionVariables() {
        return exec(session -> {
            String rrnPart = generateRrnPart();
            return session
                    .set("rrnPart", rrnPart)
                    .set("interSystemID", buildPgwInterSystemId(
                            PGW_UNIT_TEST_TERMINAL_ID, PGW_UNIT_TEST_MESSAGE_TYPE, rrnPart))
                    .set("authorizationCode", generateAuthorizationCode())
                    .set("stan", generateStan())
                    .set("orderId", generatePgwOrderIdSuffix());
        });
    }

}
