package pos.pos.restaurant.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    Optional<Restaurant> findByIdAndDeletedAtIsNull(UUID id);

    /** Whether support admin [userId] may work in [restaurantId]: every restaurant, or one given to them (user, 2026-11-19). */
    @org.springframework.data.jpa.repository.Query(value = "select coalesce((select u.admin_all_restaurants from {h-schema}users u where u.id = :userId), false)"
            + " or exists (select 1 from {h-schema}admin_restaurant_access a where a.user_id = :userId and a.restaurant_id = :restaurantId)",
            nativeQuery = true)
    boolean supportAdminMayEnter(@org.springframework.data.repository.query.Param("userId") UUID userId,
                                 @org.springframework.data.repository.query.Param("restaurantId") UUID restaurantId);

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
              AND (:currency IS NULL OR r.currency = :currency)
              AND (:timezone IS NULL OR r.timezone = :timezone)
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
              AND (:currency IS NULL OR r.currency = :currency)
              AND (:timezone IS NULL OR r.timezone = :timezone)
            """
    )
    Page<Restaurant> searchApplications(java.util.Collection<RestaurantStatus> statuses, String searchLike, String currency, String timezone,
                                        Pageable pageable);

    /** How many applications of these statuses use each time zone (the Filters' "Where"), most first. */
    @Query("SELECT r.timezone, COUNT(r) FROM Restaurant r WHERE r.deletedAt IS NULL AND r.status IN :statuses GROUP BY r.timezone ORDER BY COUNT(r) DESC")
    List<Object[]> countApplicationsByTimezone(java.util.Collection<RestaurantStatus> statuses);

    /** How many applications of these statuses use each currency, most first. */
    @Query("SELECT r.currency, COUNT(r) FROM Restaurant r WHERE r.deletedAt IS NULL AND r.status IN :statuses GROUP BY r.currency ORDER BY COUNT(r) DESC")
    List<Object[]> countApplicationsByCurrency(java.util.Collection<RestaurantStatus> statuses);

    /** The logo a restaurant (or an application) has uploaded, if any. */
    @Query("SELECT b.logoUrl FROM RestaurantBranding b WHERE b.restaurant.id = :restaurantId AND b.deletedAt IS NULL")
    java.util.Optional<String> findLogoUrl(UUID restaurantId);

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
