package gatling.config.enums;

import lombok.Getter;

/**
 * Enum representing HTTP methods for use in Gatling requests.
 */
@Getter
public enum HttpMethod {
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    DELETE("DELETE"),
    PATCH("PATCH"),
    HEAD("HEAD"),
    OPTIONS("OPTIONS");

    private final String value;

    HttpMethod(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}