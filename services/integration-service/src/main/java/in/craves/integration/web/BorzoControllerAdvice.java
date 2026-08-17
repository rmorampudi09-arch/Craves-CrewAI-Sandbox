package in.craves.integration.web;

import in.craves.integration.delivery.borzo.BorzoApiClient.BorzoApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = BorzoInternalController.class)
public class BorzoControllerAdvice {

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalidRequest(IllegalArgumentException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Borzo delivery request");
        return problem;
    }

    @ExceptionHandler(BorzoApiException.class)
    ProblemDetail providerFailure(BorzoApiException ex) {
        HttpStatus status = isConfigurationFailure(ex)
            ? HttpStatus.SERVICE_UNAVAILABLE
            : HttpStatus.BAD_GATEWAY;
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, ex.getMessage());
        problem.setTitle("Borzo provider operation failed");
        if (ex.getProviderStatus() != null) {
            problem.setProperty("providerHttpStatus", ex.getProviderStatus().value());
        }
        return problem;
    }

    private static boolean isConfigurationFailure(BorzoApiException ex) {
        String message = ex.getMessage();
        return message != null
            && (message.contains("disabled") || message.contains("not configured"));
    }
}
