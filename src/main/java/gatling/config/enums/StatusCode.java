package gatling.config.enums;

public enum StatusCode {
    // --- 2xx Success ---
    SC_OK(200),
    SC_CREATED(201),
    SC_ACCEPTED(202),
    SC_NO_CONTENT(204),
    // --- 4xx Client Error ---
    SC_BAD_REQUEST(400),
    SC_UNAUTHORIZED(401),
    SC_FORBIDDEN(403),
    SC_NOT_FOUND(404),
    SC_INTERNAL_SERVER_ERROR(500);

    private final int code;

    StatusCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
