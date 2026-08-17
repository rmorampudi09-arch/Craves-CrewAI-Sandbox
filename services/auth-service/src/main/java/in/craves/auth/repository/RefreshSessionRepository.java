package in.craves.auth.repository;

import in.craves.auth.domain.RefreshSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshSessionRepository extends JpaRepository<RefreshSession, UUID> {
    Optional<RefreshSession> findByRefreshTokenHash(String refreshTokenHash);
}
