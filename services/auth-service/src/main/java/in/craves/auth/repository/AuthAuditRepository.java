package in.craves.auth.repository;

import in.craves.auth.domain.AuthAudit;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthAuditRepository extends JpaRepository<AuthAudit, UUID> {
}
