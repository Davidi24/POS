package pos.pos.role.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.role.entity.Role;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

// checked
// tested
public interface RoleRepository extends JpaRepository<Role, UUID> {

    @Query("""
    SELECT r
    FROM Role r
    WHERE r.id IN :ids
      AND r.deletedAt IS NULL
""")
    List<Role> findByIdIn(List<UUID> ids);

    @Query("""
    SELECT r
    FROM Role r
    WHERE r.code = :code
      AND r.deletedAt IS NULL
""")
    Optional<Role> findByCode(String code);

    Optional<Role> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndIdNotAndDeletedAtIsNull(String code, UUID id);

    boolean existsByNameAndDeletedAtIsNull(String name);

    boolean existsByNameAndIdNotAndDeletedAtIsNull(String name, UUID id);

    @Query("""
    SELECT r
    FROM Role r
    WHERE r.isActive = true
      AND r.deletedAt IS NULL
""")
    List<Role> findByIsActiveTrue();

    @Query("""
    SELECT r
    FROM Role r
    WHERE r.isActive = true
      AND r.deletedAt IS NULL
    ORDER BY r.rank DESC, r.name ASC
""")
    List<Role> findByIsActiveTrueOrderByRankDescNameAsc();

    @Query("""
    SELECT r
    FROM Role r
    WHERE r.isActive = true
      AND r.isSystem = true
      AND r.deletedAt IS NULL
    ORDER BY r.rank DESC, r.name ASC
""")
    List<Role> findActiveSystemRoles();


    // It returns all roles that a user (actor) is allowed to assign.
    // All users that are below the rank that it takes as argument
    @Query("""
    SELECT r
    FROM Role r
    WHERE r.isActive = true
      AND r.deletedAt IS NULL
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
      AND r.deletedAt IS NULL
    ORDER BY r.rank DESC, r.name ASC
""")
    List<String> findActiveRoleCodesByUserId(UUID userId);

    @Query("""
    SELECT r
    FROM UserRole ur
    JOIN Role r ON ur.roleId = r.id
    WHERE ur.userId = :userId
      AND r.isActive = true
      AND r.deletedAt IS NULL
    ORDER BY r.rank DESC, r.name ASC
""")
    List<Role> findActiveRolesByUserId(UUID userId);

    // if a user has more than one role, find the highest role and give me the rank of it
    @Query("""
    SELECT COALESCE(MAX(r.rank), 0)
    FROM UserRole ur
    JOIN Role r ON ur.roleId = r.id
    WHERE ur.userId = :userId
      AND r.isActive = true
      AND r.deletedAt IS NULL
""")
    long findHighestActiveRankByUserId(UUID userId);

    // Does this user have at least one active protected role? If one of them is yes then return false. ”
    @Query("""
    SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
    FROM UserRole ur
    JOIN Role r ON ur.roleId = r.id
    WHERE ur.userId = :userId
      AND r.isActive = true
      AND r.deletedAt IS NULL
      AND r.protectedRole = true
""")
    boolean userHasProtectedActiveRole(UUID userId);

    @Query("""
    SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END
    FROM UserRole ur
    JOIN Role r ON ur.roleId = r.id
    WHERE ur.userId = :userId
      AND r.isActive = true
      AND r.deletedAt IS NULL
      AND r.code = :roleCode
""")
    boolean userHasActiveRoleCode(UUID userId, String roleCode);
}
