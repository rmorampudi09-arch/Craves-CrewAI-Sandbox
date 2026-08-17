package in.craves.order.web;

import in.craves.order.exception.OrderApiException;
import in.craves.order.web.ApiDtos.ApiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OrderApiExceptionHandler {
    @ExceptionHandler(OrderApiException.class)
    public ResponseEntity<ApiErrorResponse> handleOrderApiException(OrderApiException exception) {
        return ResponseEntity
            .status(exception.status())
            .body(new ApiErrorResponse(exception.code(), exception.getMessage()));
    }
}
