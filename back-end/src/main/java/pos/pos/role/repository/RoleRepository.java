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

    List<Role> findByIsActiveTrue();

    List<Role> findByIsActiveTrueOrderByRankDescNameAsc();

    @Query("""
    SELECT r
    FROM Role r
    WHERE r.isActive = true
      AND r.assignable = true
      AND r.protectedRole = false
      AND r.rank < :actorRank
    ORDER BY r.rank DESC, r.name ASC
""")
    List<Role> findAssignableRolesForActorRank(long actorRank);

    @Query("""
    SELECT r.code
    FROM UserRole ur
    JOIN Role r ON ur.roleId = r.id
    WHERE ur.userId = :userId
      AND r.isActive = true
""")
    List<String> findActiveRoleCodesByUserId(UUID userId);

    @Query("""
    SELECT COALESCE(MAX(r.rank), 0)
    FROM UserRole ur
    JOIN Role r ON ur.roleId = r.id
    WHERE ur.userId = :userId
      AND r.isActive = true
""")
    long findHighestActiveRankByUserId(UUID userId);

    @Query("""
    SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
    FROM UserRole ur
    JOIN Role r ON ur.roleId = r.id
    WHERE ur.userId = :userId
      AND r.isActive = true
      AND r.protectedRole = true
""")
    boolean userHasProtectedActiveRole(UUID userId);

    @Query("""
    SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
    FROM UserRole ur
    JOIN Role r ON ur.roleId = r.id
    WHERE ur.userId = :userId
      AND r.isActive = true
      AND r.code = :roleCode
""")
    boolean userHasActiveRoleCode(UUID userId, String roleCode);
}
