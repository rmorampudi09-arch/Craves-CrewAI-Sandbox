package in.craves.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_attempt")
public class LoginAttempt {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "firebase_uid", length = 128)
    private String firebaseUid;

    @Column(name = "phone_number", length = 32)
    private String phoneNumber;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    protected LoginAttempt() {
    }

    public LoginAttempt(String firebaseUid, String phoneNumber, boolean success, String failureCode, String ipAddress, String userAgent) {
        this.firebaseUid = firebaseUid;
        this.phoneNumber = phoneNumber;
        this.success = success;
        this.failureCode = failureCode;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (attemptedAt == null) {
            attemptedAt = Instant.now();
        }
    }
}
