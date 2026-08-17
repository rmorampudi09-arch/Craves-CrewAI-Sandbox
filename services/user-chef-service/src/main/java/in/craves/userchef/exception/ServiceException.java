package in.craves.userchef.exception;

public class ServiceException extends RuntimeException {
    private final int httpStatus;
    private final String code;

    public ServiceException(int httpStatus, String code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public static ServiceException badRequest(String code, String message) {
        return new ServiceException(400, code, message);
    }

    public static ServiceException loginRequired(String code, String message) {
        return new ServiceException(401, code, message);
    }

    public static ServiceException forbidden(String code, String message) {
        return new ServiceException(403, code, message);
    }

    public static ServiceException notFound(String code, String message) {
        return new ServiceException(404, code, message);
    }

    public static ServiceException conflict(String code, String message) {
        return new ServiceException(409, code, message);
    }
}
