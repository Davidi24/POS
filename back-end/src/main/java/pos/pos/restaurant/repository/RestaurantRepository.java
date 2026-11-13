package pos.pos.restaurant.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;

import java.util.Optional;
import java.util.UUID;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    Optional<Restaurant> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Restaurant> findBySlugAndDeletedAtIsNull(String slug);

    // Codes and web addresses stay reserved after a restaurant is archived (the database keeps them unique too).
    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, UUID id);

    boolean existsByCodeAndDeletedAtIsNotNull(String code);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);

    boolean existsBySlugAndDeletedAtIsNotNull(String slug);

    Optional<Restaurant> findByApplicationCodeHash(String applicationCodeHash);

    boolean existsByStatusAndPendingOwnerEmailAndDeletedAtIsNull(RestaurantStatus status, String pendingOwnerEmail);

    boolean existsByStatusAndPendingOwnerUsernameAndDeletedAtIsNull(RestaurantStatus status, String pendingOwnerUsername);

    boolean existsByOwnerIdAndIdNotAndDeletedAtIsNull(UUID ownerId, UUID id);

    /** Public applications waiting for review ([statuses] PENDING) or already rejected, newest or oldest first. */
    @Query(
            value = """
            SELECT r
            FROM Restaurant r
            WHERE r.deletedAt IS NULL
              AND r.status IN :statuses
              AND (
                    :searchLike IS NULL
                    OR lower(r.name) LIKE :searchLike
                    OR lower(r.legalName) LIKE :searchLike
                    OR lower(r.email) LIKE :searchLike
                    OR lower(r.pendingOwnerEmail) LIKE :searchLike
                    OR lower(concat(coalesce(r.pendingOwnerFirstName, ''), ' ', coalesce(r.pendingOwnerLastName, ''))) LIKE :searchLike
              )
            """,
            countQuery = """
            SELECT COUNT(r)
            FROM Restaurant r
            WHERE r.deletedAt IS NULL
              AND r.status IN :statuses
              AND (
                    :searchLike IS NULL
                    OR lower(r.name) LIKE :searchLike
                    OR lower(r.legalName) LIKE :searchLike
                    OR lower(r.email) LIKE :searchLike
                    OR lower(r.pendingOwnerEmail) LIKE :searchLike
                    OR lower(concat(coalesce(r.pendingOwnerFirstName, ''), ' ', coalesce(r.pendingOwnerLastName, ''))) LIKE :searchLike
              )
            """
    )
    Page<Restaurant> searchApplications(java.util.Collection<RestaurantStatus> statuses, String searchLike, Pageable pageable);

    @Query(
            value = """
            SELECT r
            FROM Restaurant r
            WHERE r.deletedAt IS NULL
              AND (:active IS NULL OR r.isActive = :active)
              AND (:status IS NULL OR r.status = :status)
              AND (:ownerUserId IS NULL OR r.ownerId = :ownerUserId)
              AND (
                    :searchLike IS NULL
                    OR lower(r.name) LIKE :searchLike
                    OR lower(r.legalName) LIKE :searchLike
                    OR lower(r.code) LIKE :searchLike
                    OR lower(r.slug) LIKE :searchLike
                    OR lower(r.email) LIKE :searchLike
                    OR lower(r.phone) LIKE :searchLike
              )
              AND (
                    :superAdmin = true
                    OR (:actorRestaurantId IS NOT NULL AND r.id = :actorRestaurantId)
                    OR r.ownerId = :actorUserId
              )
            """,
            countQuery = """
            SELECT COUNT(r)
            FROM Restaurant r
            WHERE r.deletedAt IS NULL
              AND (:active IS NULL OR r.isActive = :active)
              AND (:status IS NULL OR r.status = :status)
              AND (:ownerUserId IS NULL OR r.ownerId = :ownerUserId)
              AND (
                    :searchLike IS NULL
                    OR lower(r.name) LIKE :searchLike
                    OR lower(r.legalName) LIKE :searchLike
                    OR lower(r.code) LIKE :searchLike
                    OR lower(r.slug) LIKE :searchLike
                    OR lower(r.email) LIKE :searchLike
                    OR lower(r.phone) LIKE :searchLike
              )
              AND (
                    :superAdmin = true
                    OR (:actorRestaurantId IS NOT NULL AND r.id = :actorRestaurantId)
                    OR r.ownerId = :actorUserId
              )
            """
    )
    Page<Restaurant> searchVisibleRestaurants(
            Boolean active,
            RestaurantStatus status,
            UUID ownerUserId,
            String searchLike,
            boolean superAdmin,
            UUID actorUserId,
            UUID actorRestaurantId,
            Pageable pageable
    );
}
