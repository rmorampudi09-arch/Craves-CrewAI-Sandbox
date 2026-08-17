package in.craves.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@IdClass(AuthIdentityRoleId.class)
@Table(name = "auth_identity_role")
public class AuthIdentityRole {
    @Id
    @Column(name = "identity_id", nullable = false)
    private UUID identityId;

    @Id
    @Column(name = "role_code", nullable = false, length = 32)
    private String roleCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuthIdentityRole() {
    }

    public AuthIdentityRole(UUID identityId, String roleCode) {
        this.identityId = identityId;
        this.roleCode = roleCode;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getIdentityId() {
        return identityId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
