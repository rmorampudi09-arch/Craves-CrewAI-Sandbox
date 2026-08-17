package in.craves.auth.api;

import jakarta.validation.constraints.NotBlank;

public record FirebaseExchangeRequest(
    @NotBlank(message = "firebaseIdToken is required")
    String firebaseIdToken
) {
}
