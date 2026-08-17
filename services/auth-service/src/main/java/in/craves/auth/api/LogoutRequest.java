package in.craves.auth.api;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
    @NotBlank(message = "refreshToken is required")
    String refreshToken
) {
}
