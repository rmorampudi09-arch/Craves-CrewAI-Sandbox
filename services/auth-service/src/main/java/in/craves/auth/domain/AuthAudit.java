package in.craves.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_audit")
public class AuthAudit {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "identity_id")
    private UUID identityId;

    @Column(name = "action", nullable = false, length = 80)
    private String action;

    @Column(name = "actor_identity_id")
    private UUID actorIdentityId;

    @Column(name = "details", columnDefinition = "text")
    private String details;

    @Column(name = "correlation_id", length = 80)
    private String correlationId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuthAudit() {
    }

    public AuthAudit(UUID identityId, String action, String details, String ipAddress, String userAgent) {
        this.identityId = identityId;
        this.action = action;
        this.details = details;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
