package in.craves.auth.repository;

import in.craves.auth.domain.LoginAttempt;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, UUID> {
}
