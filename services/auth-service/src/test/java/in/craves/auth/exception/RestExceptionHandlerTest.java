package in.craves.auth.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class RestExceptionHandlerTest {
    private final RestExceptionHandler handler = new RestExceptionHandler();

    @Test
    void preservesResponseStatusExceptionStatusAndReason() {
        var response = handler.handleResponseStatus(
            new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Internal administrator role management is not enabled"
            )
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("SERVICE_UNAVAILABLE");
        assertThat(response.getBody().message())
            .isEqualTo("Internal administrator role management is not enabled");
    }

    @Test
    void unexpectedExceptionStillReturnsGenericInternalServerError() {
        var response = handler.handleUnexpected(new IllegalStateException("sensitive detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Unexpected service error");
    }
}
