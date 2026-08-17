package in.craves.auth.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class AuthIdentityRoleId implements Serializable {
    private UUID identityId;
    private String roleCode;

    public AuthIdentityRoleId() {
    }

    public AuthIdentityRoleId(UUID identityId, String roleCode) {
        this.identityId = identityId;
        this.roleCode = roleCode;
    }

    public UUID getIdentityId() {
        return identityId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AuthIdentityRoleId that)) {
            return false;
        }
        return Objects.equals(identityId, that.identityId) && Objects.equals(roleCode, that.roleCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identityId, roleCode);
    }
}
