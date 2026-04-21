package pos.pos.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
              AND (
                    :superAdmin = true
                    OR (
                        COALESCE((
                            SELECT MAX(r2.rank)
                            FROM UserRole ur2
                            JOIN Role r2 ON ur2.roleId = r2.id
                            WHERE ur2.userId = u.id
                              AND r2.isActive = true
                        ), 0) < :actorRank
                        AND NOT EXISTS (
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
              AND (
                    :superAdmin = true
                    OR (
                        COALESCE((
                            SELECT MAX(r2.rank)
                            FROM UserRole ur2
                            JOIN Role r2 ON ur2.roleId = r2.id
                            WHERE ur2.userId = u.id
                              AND r2.isActive = true
                        ), 0) < :actorRank
                        AND NOT EXISTS (
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
    Page<User> searchVisibleUsers(
            Boolean active,
            String searchLike,
            String normalizedPhoneLike,
            String roleCode,
            boolean superAdmin,
            long actorRank,
            Pageable pageable
    );
}
