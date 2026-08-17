package in.craves.subscription.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException ex, HttpServletRequest request) {
        return ResponseEntity.status(ex.getStatus()).body(new ApiError(
            Instant.now(),
            ex.getCode(),
            ex.getMessage(),
            request.getRequestURI(),
            List.of()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
            .map(this::format)
            .toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiError(
            Instant.now(),
            "VALIDATION_FAILED",
            "Request validation failed",
            request.getRequestURI(),
            details
        ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus resolved = HttpStatus.resolve(ex.getStatusCode().value());
        String code = resolved == null ? "HTTP_" + ex.getStatusCode().value() : resolved.name();
        String message = ex.getReason() == null || ex.getReason().isBlank()
            ? "Request failed"
            : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(new ApiError(
            Instant.now(),
            code,
            message,
            request.getRequestURI(),
            List.of()
        ));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(
            Instant.now(),
            "INTERNAL_SERVER_ERROR",
            "Internal server error",
            request.getRequestURI(),
            List.of()
        ));
    }

    private String format(FieldError error) {
        return error.getField() + ": " + error.getDefaultMessage();
    }

    public record ApiError(Instant timestamp, String code, String message, String path, List<String> details) {
    }
}
