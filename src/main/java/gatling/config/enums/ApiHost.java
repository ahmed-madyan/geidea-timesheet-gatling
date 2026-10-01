package gatling.config.enums;

import java.net.URI;

/**
 * Host selected for one product in one region. Each region implements this
 * in its own class ({@code KsaBaseUri}, {@code UaeBaseUri}, {@code EgyptBaseUri}).
 */
public interface ApiHost {
    URI uri();
}
