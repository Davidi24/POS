package pos.pos.user.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pos.pos.user.entity.User;

import java.util.Optional;
import java.util.UUID;


public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByIdAndDeletedAtIsNull(UUID id);

    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    Optional<User> findByUsernameAndDeletedAtIsNull(String username);

    Optional<User> findByNormalizedPhoneAndDeletedAtIsNull(String normalizedPhone);

    boolean existsByEmailAndDeletedAtIsNull(String email);

    boolean existsByUsernameAndDeletedAtIsNull(String username);

    boolean existsByNormalizedPhoneAndDeletedAtIsNull(String normalizedPhone);

    boolean existsByNormalizedPhoneAndIdNotAndDeletedAtIsNull(String normalizedPhone, UUID id);

    @Query("""
        SELECT u
        FROM User u
        WHERE u.id = :userId
          AND u.deletedAt IS NULL
          AND u.isActive = true
    """)
    Optional<User> findActiveById(UUID userId);

    // True when the person works at a restaurant that is suspended, closed or removed; they can't sign in then.
    @Query("""
        SELECT COUNT(r) > 0
        FROM User u, Restaurant r
        WHERE u.id = :userId
          AND r.id = u.restaurantId
          AND (r.deletedAt IS NOT NULL OR r.isActive = false OR r.status <> pos.pos.restaurant.enums.RestaurantStatus.ACTIVE)
    """)
    boolean worksAtClosedRestaurant(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT u
        FROM User u
        WHERE u.id = :userId
          AND u.deletedAt IS NULL
          AND u.isActive = true
    """)
    Optional<User> findActiveByIdForUpdate(@Param("userId") UUID userId);

    // Active staff of a restaurant who work in this branch (or aren't tied to one branch).
    @Query("""
        SELECT u.id
        FROM User u
        WHERE u.restaurantId = :restaurantId
          AND u.deletedAt IS NULL
          AND u.isActive = true
          AND (u.defaultBranchId IS NULL OR u.defaultBranchId = :branchId)
    """)
    java.util.List<UUID> findActiveStaffIds(UUID restaurantId, UUID branchId);

    // The same staff, only those whose role has this permission (e.g. managers who approve bookings).
    @Query("""
        SELECT DISTINCT u.id
        FROM User u, UserRole ur, RolePermission rp, Permission p
        WHERE ur.userId = u.id AND rp.roleId = ur.roleId AND p.id = rp.permissionId AND p.code = :permission
          AND u.restaurantId = :restaurantId
          AND u.deletedAt IS NULL
          AND u.isActive = true
          AND (u.defaultBranchId IS NULL OR u.defaultBranchId = :branchId)
    """)
    java.util.List<UUID> findActiveStaffIdsWithPermission(UUID restaurantId, UUID branchId, String permission);

    // The same staff with one of these roles. Owners count wherever their default branch is.
    @Query("""
        SELECT DISTINCT u.id
        FROM User u, UserRole ur, Role r
        WHERE ur.userId = u.id AND r.id = ur.roleId AND r.code IN :roles
          AND u.restaurantId = :restaurantId
          AND u.deletedAt IS NULL
          AND u.isActive = true
          AND (u.defaultBranchId IS NULL OR u.defaultBranchId = :branchId OR r.code IN ('OWNER', 'CO_OWNER'))
    """)
    java.util.List<UUID> findActiveStaffIdsWithRoles(UUID restaurantId, UUID branchId, java.util.Collection<String> roles);

    @Query(
            value = """
            SELECT u
            FROM User u
            WHERE u.deletedAt IS NULL
              AND (:active IS NULL OR u.isActive = :active)
              AND (
                    :searchLike IS NULL
                    OR lower(u.email) LIKE :searchLike
                    OR lower(u.username) LIKE :searchLike
                    OR lower(u.firstName) LIKE :searchLike
                    OR lower(u.lastName) LIKE :searchLike
                    OR (:normalizedPhoneLike IS NOT NULL AND u.normalizedPhone LIKE :normalizedPhoneLike)
              )
              AND (
                    :roleCode IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM UserRole ur
                        JOIN Role r ON ur.roleId = r.id
                        WHERE ur.userId = u.id
                          AND r.isActive = true
                          AND r.code = :roleCode
                    )
              )
              AND ((:superAdmin = true AND :actorRestaurantId IS NULL) OR u.restaurantId = :actorRestaurantId)
              AND (:actorId IS NULL OR u.id <> :actorId)
              AND (
                    (
                        COALESCE((
                            SELECT MAX(r2.rank)
                            FROM UserRole ur2
                            JOIN Role r2 ON ur2.roleId = r2.id
                            WHERE ur2.userId = u.id
                              AND r2.isActive = true
                        ), 0) < :actorRank
                        OR (
                            u.createdBy = :actorId
                            AND COALESCE((
                                SELECT MAX(r4.rank)
                                FROM UserRole ur4
                                JOIN Role r4 ON ur4.roleId = r4.id
                                WHERE ur4.userId = u.id
                                  AND r4.isActive = true
                            ), 0) = :actorRank
                        )
                    )
                    AND (
                        :superAdmin = true
                        OR NOT EXISTS (
                            SELECT 1
                            FROM UserRole ur3
                            JOIN Role r3 ON ur3.roleId = r3.id
                            WHERE ur3.userId = u.id
                              AND r3.isActive = true
                              AND r3.protectedRole = true
                        )
                    )
              )
            """,
            countQuery = """
            SELECT COUNT(u)
            FROM User u
            WHERE u.deletedAt IS NULL
              AND (:active IS NULL OR u.isActive = :active)
              AND (
                    :searchLike IS NULL
                    OR lower(u.email) LIKE :searchLike
                    OR lower(u.username) LIKE :searchLike
                    OR lower(u.firstName) LIKE :searchLike
                    OR lower(u.lastName) LIKE :searchLike
                    OR (:normalizedPhoneLike IS NOT NULL AND u.normalizedPhone LIKE :normalizedPhoneLike)
              )
              AND (
                    :roleCode IS NULL
                    OR EXISTS (
                        SELECT 1
                        FROM UserRole ur
                        JOIN Role r ON ur.roleId = r.id
                        WHERE ur.userId = u.id
                          AND r.isActive = true
                          AND r.code = :roleCode
                    )
              )
              AND ((:superAdmin = true AND :actorRestaurantId IS NULL) OR u.restaurantId = :actorRestaurantId)
              AND (:actorId IS NULL OR u.id <> :actorId)
              AND (
                    (
                        COALESCE((
                            SELECT MAX(r2.rank)
                            FROM UserRole ur2
                            JOIN Role r2 ON ur2.roleId = r2.id
                            WHERE ur2.userId = u.id
                              AND r2.isActive = true
                        ), 0) < :actorRank
                        OR (
                            u.createdBy = :actorId
                            AND COALESCE((
                                SELECT MAX(r4.rank)
                                FROM UserRole ur4
                                JOIN Role r4 ON ur4.roleId = r4.id
                                WHERE ur4.userId = u.id
                                  AND r4.isActive = true
                            ), 0) = :actorRank
                        )
                    )
                    AND (
                        :superAdmin = true
                        OR NOT EXISTS (
                            SELECT 1
                            FROM UserRole ur3
                            JOIN Role r3 ON ur3.roleId = r3.id
                            WHERE ur3.userId = u.id
                              AND r3.isActive = true
                              AND r3.protectedRole = true
                        )
                    )
              )
            """
    )
    // superAdmin: the Super Admin or a Co-Super-Admin (every restaurant, protected roles included); inside a restaurant
    // they entered (actorRestaurantId) they see only its people. Everyone still only sees people ranked below them, plus
    // the people at their own level they added themselves (actorId); the Super Admin's rank is Long.MAX_VALUE, so it
    // sees all. Nobody sees themselves in the staff list (user rule).
    Page<User> searchVisibleUsers(
            Boolean active,
            String searchLike,
            String normalizedPhoneLike,
            String roleCode,
            boolean superAdmin,
            long actorRank,
            UUID actorId,
            UUID actorRestaurantId,
            Pageable pageable
    );
}
