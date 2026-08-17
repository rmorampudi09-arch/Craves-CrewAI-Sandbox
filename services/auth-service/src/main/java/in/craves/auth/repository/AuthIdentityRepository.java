package in.craves.auth.repository;

import in.craves.auth.domain.AuthIdentity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthIdentityRepository extends JpaRepository<AuthIdentity, UUID> {
    Optional<AuthIdentity> findByFirebaseUid(String firebaseUid);

    Optional<AuthIdentity> findByPhoneNumber(String phoneNumber);
}
