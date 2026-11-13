package pos.pos.restaurant.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.enums.BranchStatus;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    Optional<Branch> findByIdAndRestaurantIdAndDeletedAtIsNull(UUID id, UUID restaurantId);

    // Serialize staff assignment with branch removal so a concurrent request cannot restore a removed selection.
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Branch b where b.id=:id and b.restaurant.id=:restaurantId and b.deletedAt is null")
    Optional<Branch> findForAssignment(@Param("id") UUID id, @Param("restaurantId") UUID restaurantId);

    boolean existsByIdAndRestaurantIdAndDeletedAtIsNull(UUID id, UUID restaurantId);

    java.util.List<Branch> findAllByRestaurantIdAndDeletedAtIsNull(UUID restaurantId);

    long countByRestaurantIdAndDeletedAtIsNull(UUID restaurantId);

    long countByRestaurantIdAndIsActiveTrueAndDeletedAtIsNull(UUID restaurantId);

    // Bulk soft-delete: single native UPDATE instead of loading + looping over each branch.
    @Modifying
    @Query(value = """
            UPDATE {h-schema}branches
            SET is_active = false, status = 'ARCHIVED',
                deleted_at = :deletedAt, updated_by = :actorId
            WHERE restaurant_id = :restaurantId AND deleted_at IS NULL
            """, nativeQuery = true)
    void softDeleteAllByRestaurantId(
            @Param("restaurantId") UUID restaurantId,
            @Param("deletedAt") OffsetDateTime deletedAt,
            @Param("actorId") UUID actorId
    );

    // A removed branch keeps its code, so it is never handed to a new branch of the same restaurant.
    boolean existsByRestaurantIdAndCode(UUID restaurantId, String code);

    boolean existsByRestaurantIdAndCodeAndIdNot(UUID restaurantId, String code, UUID id);

    boolean existsByRestaurantIdAndCodeAndDeletedAtIsNotNull(UUID restaurantId, String code);

    @Modifying
    @Query(value = """
            UPDATE {h-schema}branches
            SET manager_user_id = NULL, updated_by = :actorId, updated_at = :now
            WHERE restaurant_id = :restaurantId AND manager_user_id = :userId
            """, nativeQuery = true)
    int clearManager(
            @Param("restaurantId") UUID restaurantId,
            @Param("userId") UUID userId,
            @Param("actorId") UUID actorId,
            @Param("now") OffsetDateTime now
    );

    @EntityGraph(attributePaths = "restaurant")
    Optional<Branch> findByRestaurant_SlugAndCodeAndDeletedAtIsNull(String restaurantSlug, String code);

    @Query(
            value = """
            SELECT b
            FROM Branch b
            WHERE b.deletedAt IS NULL
              AND b.restaurant.id = :restaurantId
              AND (:active IS NULL OR b.isActive = :active)
              AND (:status IS NULL OR b.status = :status)
              AND (:managerUserId IS NULL OR b.managerUserId = :managerUserId)
              AND (
                    :searchLike IS NULL
                    OR lower(b.name) LIKE :searchLike
                    OR lower(b.code) LIKE :searchLike
                    OR lower(b.email) LIKE :searchLike
                    OR lower(b.phone) LIKE :searchLike
                    OR lower(b.description) LIKE :searchLike
              )
            """,
            countQuery = """
            SELECT COUNT(b)
            FROM Branch b
            WHERE b.deletedAt IS NULL
              AND b.restaurant.id = :restaurantId
              AND (:active IS NULL OR b.isActive = :active)
              AND (:status IS NULL OR b.status = :status)
              AND (:managerUserId IS NULL OR b.managerUserId = :managerUserId)
              AND (
                    :searchLike IS NULL
                    OR lower(b.name) LIKE :searchLike
                    OR lower(b.code) LIKE :searchLike
                    OR lower(b.email) LIKE :searchLike
                    OR lower(b.phone) LIKE :searchLike
                    OR lower(b.description) LIKE :searchLike
              )
            """
    )
    Page<Branch> searchRestaurantBranches(
            UUID restaurantId,
            Boolean active,
            BranchStatus status,
            UUID managerUserId,
            String searchLike,
            Pageable pageable
    );
}
