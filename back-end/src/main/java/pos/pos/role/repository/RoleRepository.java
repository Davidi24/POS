package pos.pos.role.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.role.entity.Role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    List<Role> findByIdIn(List<UUID> ids);

    Optional<Role> findByCode(String code);

    Optional<Role> findByName(String name);

    boolean existsByCode(String code);

    boolean existsByName(String name);

    List<Role> findByIsActiveTrue();

    @Query("""
    SELECT r.code
    FROM UserRole ur
    JOIN Role r ON ur.roleId = r.id
    WHERE ur.userId = :userId
      AND r.isActive = true
""")
    List<String> findActiveRoleCodesByUserId(UUID userId);
}