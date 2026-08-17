package in.craves.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "auth_role")
public class AuthRole {
    @Id
    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    protected AuthRole() {
    }

    public AuthRole(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
