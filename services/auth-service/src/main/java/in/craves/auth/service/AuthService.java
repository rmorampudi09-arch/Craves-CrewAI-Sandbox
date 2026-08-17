package in.craves.auth.service;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import in.craves.auth.api.AuthTokenResponse;
import in.craves.auth.api.FirebaseExchangeRequest;
import in.craves.auth.api.IdentityResponse;
import in.craves.auth.api.InternalIdentityEmailResponse;
import in.craves.auth.domain.AuthAudit;
import in.craves.auth.domain.AuthIdentity;
import in.craves.auth.domain.AuthIdentityRole;
import in.craves.auth.domain.LoginAttempt;
import in.craves.auth.domain.RefreshSession;
import in.craves.auth.exception.AuthException;
import in.craves.auth.config.JwtProperties;
import in.craves.auth.repository.AuthAuditRepository;
import in.craves.auth.repository.AuthIdentityRepository;
import in.craves.auth.repository.AuthIdentityRoleRepository;
import in.craves.auth.repository.AuthRoleRepository;
import in.craves.auth.repository.LoginAttemptRepository;
import in.craves.auth.repository.RefreshSessionRepository;
import in.craves.auth.security.CravesJwtService;
import in.craves.auth.security.CurrentUser;
import in.craves.auth.security.RefreshTokenGenerator;
import in.craves.auth.security.TokenHasher;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {
    private static final String ROLE_CUSTOMER = "CUSTOMER";
    private static final String ROLE_CHEF = "CHEF";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final FirebaseApp firebaseApp;
    private final JwtProperties jwtProperties;
    private final AuthIdentityRepository identityRepository;
    private final AuthRoleRepository roleRepository;
    private final AuthIdentityRoleRepository identityRoleRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final LoginAttemptRepository loginAttemptRepository;
    private final AuthAuditRepository authAuditRepository;
    private final CravesJwtService jwtService;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final TokenHasher tokenHasher;

    public AuthService(
        FirebaseApp firebaseApp,
        JwtProperties jwtProperties,
        AuthIdentityRepository identityRepository,
        AuthRoleRepository roleRepository,
        AuthIdentityRoleRepository identityRoleRepository,
        RefreshSessionRepository refreshSessionRepository,
        LoginAttemptRepository loginAttemptRepository,
        AuthAuditRepository authAuditRepository,
        CravesJwtService jwtService,
        RefreshTokenGenerator refreshTokenGenerator,
        TokenHasher tokenHasher
    ) {
        this.firebaseApp = firebaseApp;
        this.jwtProperties = jwtProperties;
        this.identityRepository = identityRepository;
        this.roleRepository = roleRepository;
        this.identityRoleRepository = identityRoleRepository;
        this.refreshSessionRepository = refreshSessionRepository;
        this.loginAttemptRepository = loginAttemptRepository;
        this.authAuditRepository = authAuditRepository;
        this.jwtService = jwtService;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.tokenHasher = tokenHasher;
    }

    @Transactional
    public AuthTokenResponse exchangeFirebaseToken(FirebaseExchangeRequest request, HttpServletRequest httpRequest) {
        String ipAddress = clientIp(httpRequest);
        String userAgent = truncate(httpRequest.getHeader("User-Agent"), 512);
        try {
            FirebaseToken decodedToken = FirebaseAuth.getInstance(firebaseApp).verifyIdToken(request.firebaseIdToken(), true);
            String firebaseUid = decodedToken.getUid();
            String phoneNumber = firebasePhoneNumber(decodedToken);
            if (!StringUtils.hasText(phoneNumber)) {
                saveLoginAttempt(firebaseUid, null, false, "PHONE_NUMBER_MISSING", ipAddress, userAgent);
                throw AuthException.unauthorized("PHONE_NUMBER_MISSING", "Firebase token does not contain a verified phone number");
            }

            AuthIdentity identity = loadOrCreateIdentity(decodedToken, phoneNumber);
            identity.setLastLoginAt(Instant.now());
            identity = identityRepository.save(identity);
            ensureCustomerRole(identity.getId());

            List<String> roles = identityRoleRepository.findRoleCodesByIdentityId(identity.getId());
            saveLoginAttempt(firebaseUid, phoneNumber, true, null, ipAddress, userAgent);
            saveAudit(identity.getId(), "FIREBASE_EXCHANGE", "Firebase phone token exchanged", ipAddress, userAgent);
            return issueTokenPair(identity, roles, userAgent, ipAddress);
        } catch (FirebaseAuthException ex) {
            saveLoginAttempt(null, null, false, "FIREBASE_TOKEN_INVALID", ipAddress, userAgent);
            throw AuthException.unauthorized("FIREBASE_TOKEN_INVALID", "Firebase ID token is invalid or expired");
        }
    }

    @Transactional
    public AuthTokenResponse refresh(String refreshToken, HttpServletRequest httpRequest) {
        String hash = tokenHasher.sha256Base64Url(refreshToken);
        RefreshSession session = refreshSessionRepository.findByRefreshTokenHash(hash)
            .orElseThrow(() -> AuthException.unauthorized("INVALID_REFRESH_TOKEN", "Refresh token is invalid"));

        Instant now = Instant.now();
        if (session.isRevoked()) {
            throw AuthException.unauthorized("REFRESH_TOKEN_REVOKED", "Refresh token has been revoked");
        }
        if (session.isExpired(now)) {
            session.setRevokedAt(now);
            session.setRevokeReason("EXPIRED");
            refreshSessionRepository.save(session);
            throw AuthException.unauthorized("REFRESH_TOKEN_EXPIRED", "Refresh token has expired");
        }

        AuthIdentity identity = identityRepository.findById(session.getIdentityId())
            .orElseThrow(() -> AuthException.unauthorized("IDENTITY_NOT_FOUND", "Identity was not found"));
        assertActive(identity);

        String ipAddress = clientIp(httpRequest);
        String userAgent = truncate(httpRequest.getHeader("User-Agent"), 512);
        String newRefreshToken = refreshTokenGenerator.generate();
        RefreshSession newSession = new RefreshSession(
            identity.getId(),
            tokenHasher.sha256Base64Url(newRefreshToken),
            userAgent,
            ipAddress,
            now.plus(jwtProperties.getRefreshTokenTtl())
        );
        newSession = refreshSessionRepository.save(newSession);

        session.setLastUsedAt(now);
        session.setRevokedAt(now);
        session.setRevokeReason("ROTATED");
        session.setReplacedBySessionId(newSession.getId());
        refreshSessionRepository.save(session);

        List<String> roles = identityRoleRepository.findRoleCodesByIdentityId(identity.getId());
        saveAudit(identity.getId(), "REFRESH_ROTATED", "Refresh token rotated", ipAddress, userAgent);
        return buildTokenResponse(identity, roles, newRefreshToken, newSession.getExpiresAt());
    }

    @Transactional
    public void logout(String refreshToken, HttpServletRequest httpRequest) {
        String hash = tokenHasher.sha256Base64Url(refreshToken);
        Optional<RefreshSession> optionalSession = refreshSessionRepository.findByRefreshTokenHash(hash);
        if (optionalSession.isEmpty()) {
            return;
        }
        RefreshSession session = optionalSession.get();
        if (!session.isRevoked()) {
            session.setRevokedAt(Instant.now());
            session.setRevokeReason("USER_LOGOUT");
            refreshSessionRepository.save(session);
            saveAudit(session.getIdentityId(), "LOGOUT", "Refresh session revoked", clientIp(httpRequest), truncate(httpRequest.getHeader("User-Agent"), 512));
        }
    }

    @Transactional(readOnly = true)
    public IdentityResponse me(CurrentUser currentUser) {
        AuthIdentity identity = identityRepository.findById(currentUser.identityId())
            .orElseThrow(() -> AuthException.unauthorized("IDENTITY_NOT_FOUND", "Identity was not found"));
        assertActive(identity);
        List<String> roles = identityRoleRepository.findRoleCodesByIdentityId(identity.getId());
        return toIdentityResponse(identity, roles);
    }

    @Transactional(readOnly = true)
    public InternalIdentityEmailResponse internalIdentityEmail(UUID identityId) {
        AuthIdentity identity = identityRepository.findById(identityId)
            .orElseThrow(() -> AuthException.badRequest("IDENTITY_NOT_FOUND", "Identity was not found"));
        return new InternalIdentityEmailResponse(
            identity.getId(),
            identity.getEmail(),
            identity.isEmailVerified(),
            identity.getStatus()
        );
    }

    @Transactional
    public IdentityResponse grantChefRole(UUID identityId, UUID sourceApplicationId) {
        AuthIdentity identity = identityRepository.findById(identityId)
            .orElseThrow(() -> AuthException.badRequest("IDENTITY_NOT_FOUND", "Identity was not found"));
        assertActive(identity);
        ensureRole(identity.getId(), ROLE_CHEF);
        List<String> roles = identityRoleRepository.findRoleCodesByIdentityId(identity.getId());
        saveAudit(identity.getId(), "CHEF_ROLE_GRANTED", "Chef role granted from application " + sourceApplicationId, null, null);
        return toIdentityResponse(identity, roles);
    }

    private AuthIdentity loadOrCreateIdentity(FirebaseToken decodedToken, String phoneNumber) {
        String firebaseUid = decodedToken.getUid();
        Optional<AuthIdentity> byUid = identityRepository.findByFirebaseUid(firebaseUid);
        Optional<AuthIdentity> byPhone = identityRepository.findByPhoneNumber(phoneNumber);

        if (byUid.isEmpty() && byPhone.isPresent()) {
            throw AuthException.conflict("PHONE_ALREADY_LINKED", "Phone number is already linked to another identity");
        }

        AuthIdentity identity = byUid.orElseGet(() -> new AuthIdentity(firebaseUid, phoneNumber));
        if (byPhone.isPresent() && !byPhone.get().getId().equals(identity.getId())) {
            throw AuthException.conflict("PHONE_ALREADY_LINKED", "Phone number is already linked to another identity");
        }

        identity.setFirebaseUid(firebaseUid);
        identity.setPhoneNumber(phoneNumber);
        identity.setEmail(emptyToNull(decodedToken.getEmail()));
        identity.setEmailVerified(decodedToken.isEmailVerified());
        identity.setDisplayName(truncate(emptyToNull(decodedToken.getName()), 160));
        if (!StringUtils.hasText(identity.getStatus())) {
            identity.setStatus(STATUS_ACTIVE);
        }
        assertActive(identity);
        return identity;
    }

    private void ensureCustomerRole(UUID identityId) {
        ensureRole(identityId, ROLE_CUSTOMER);
    }

    private void ensureRole(UUID identityId, String roleCode) {
        if (!roleRepository.existsById(roleCode)) {
            throw new IllegalStateException(roleCode + " role is missing from auth_role");
        }
        if (!identityRoleRepository.existsByIdentityIdAndRoleCode(identityId, roleCode)) {
            identityRoleRepository.save(new AuthIdentityRole(identityId, roleCode));
        }
    }

    private AuthTokenResponse issueTokenPair(AuthIdentity identity, List<String> roles, String userAgent, String ipAddress) {
        Instant expiresAt = Instant.now().plus(jwtProperties.getRefreshTokenTtl());
        String refreshToken = refreshTokenGenerator.generate();
        RefreshSession session = new RefreshSession(
            identity.getId(),
            tokenHasher.sha256Base64Url(refreshToken),
            userAgent,
            ipAddress,
            expiresAt
        );
        refreshSessionRepository.save(session);
        return buildTokenResponse(identity, roles, refreshToken, expiresAt);
    }

    private AuthTokenResponse buildTokenResponse(AuthIdentity identity, List<String> roles, String refreshToken, Instant refreshTokenExpiresAt) {
        String accessToken = jwtService.issueAccessToken(identity, roles);
        return AuthTokenResponse.create(
            accessToken,
            jwtProperties.getAccessTokenTtl().toSeconds(),
            refreshToken,
            refreshTokenExpiresAt,
            toIdentityResponse(identity, roles)
        );
    }

    private IdentityResponse toIdentityResponse(AuthIdentity identity, List<String> roles) {
        return new IdentityResponse(
            identity.getId(),
            identity.getFirebaseUid(),
            identity.getPhoneNumber(),
            identity.getEmail(),
            identity.isEmailVerified(),
            identity.getDisplayName(),
            identity.getStatus(),
            roles,
            identity.getLastLoginAt()
        );
    }

    private void assertActive(AuthIdentity identity) {
        if (!STATUS_ACTIVE.equals(identity.getStatus())) {
            throw AuthException.forbidden("IDENTITY_NOT_ACTIVE", "Identity is not active");
        }
    }

    private void saveLoginAttempt(String firebaseUid, String phoneNumber, boolean success, String failureCode, String ipAddress, String userAgent) {
        loginAttemptRepository.save(new LoginAttempt(firebaseUid, phoneNumber, success, failureCode, ipAddress, userAgent));
    }

    private void saveAudit(UUID identityId, String action, String details, String ipAddress, String userAgent) {
        authAuditRepository.save(new AuthAudit(identityId, action, details, ipAddress, userAgent));
    }

    private static String firebasePhoneNumber(FirebaseToken decodedToken) {
        Object value = decodedToken.getClaims().get("phone_number");
        return value == null ? null : String.valueOf(value);
    }

    private static String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return truncate(forwardedFor.split(",")[0].trim(), 64);
        }
        return truncate(request.getRemoteAddr(), 64);
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
