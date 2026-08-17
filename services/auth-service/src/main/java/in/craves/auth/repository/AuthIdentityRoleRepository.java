package in.craves.auth.repository;

import in.craves.auth.domain.AuthIdentityRole;
import in.craves.auth.domain.AuthIdentityRoleId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthIdentityRoleRepository extends JpaRepository<AuthIdentityRole, AuthIdentityRoleId> {
    boolean existsByIdentityIdAndRoleCode(UUID identityId, String roleCode);

    @Query("select r.roleCode from AuthIdentityRole r where r.identityId = :identityId order by r.roleCode")
    List<String> findRoleCodesByIdentityId(@Param("identityId") UUID identityId);
}
