package in.craves.auth.web;

import in.craves.auth.api.AuthTokenResponse;
import in.craves.auth.api.FirebaseExchangeRequest;
import in.craves.auth.api.LogoutRequest;
import in.craves.auth.api.LogoutResponse;
import in.craves.auth.api.MeResponse;
import in.craves.auth.api.RefreshTokenRequest;
import in.craves.auth.exception.AuthException;
import in.craves.auth.security.CurrentUser;
import in.craves.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/firebase/exchange")
    public AuthTokenResponse exchangeFirebaseToken(@Valid @RequestBody FirebaseExchangeRequest request, HttpServletRequest httpRequest) {
        return authService.exchangeFirebaseToken(request, httpRequest);
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
        return authService.refresh(request.refreshToken(), httpRequest);
    }

    @PostMapping("/logout")
    public LogoutResponse logout(@Valid @RequestBody LogoutRequest request, HttpServletRequest httpRequest) {
        authService.logout(request.refreshToken(), httpRequest);
        return new LogoutResponse(true);
    }

    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            throw AuthException.unauthorized("AUTHENTICATION_REQUIRED", "Authentication is required");
        }
        return new MeResponse(authService.me(currentUser));
    }
}
