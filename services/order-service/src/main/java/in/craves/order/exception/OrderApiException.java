package in.craves.order.exception;

import org.springframework.http.HttpStatus;

public class OrderApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public OrderApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static OrderApiException badRequest(String code, String message) {
        return new OrderApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static OrderApiException notFound(String code, String message) {
        return new OrderApiException(HttpStatus.NOT_FOUND, code, message);
    }

    public static OrderApiException conflict(String code, String message) {
        return new OrderApiException(HttpStatus.CONFLICT, code, message);
    }

    public static OrderApiException serviceUnavailable(String code, String message) {
        return new OrderApiException(HttpStatus.SERVICE_UNAVAILABLE, code, message);
    }
}
