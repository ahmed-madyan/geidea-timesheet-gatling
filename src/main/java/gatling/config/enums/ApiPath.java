package gatling.config.enums;

/**
 * Request path owned by one region. Each region implements this in its own
 * class ({@code KsaBasePath}, {@code UaeBasePath}, {@code EgyptBasePath}).
 */
public interface ApiPath {
    String path();
}
