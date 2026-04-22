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

    boolean existsByCodeAndDeletedAtIsNull(String code);

    boolean existsByCodeAndIdNotAndDeletedAtIsNull(String code, UUID id);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySlugAndIdNotAndDeletedAtIsNull(String slug, UUID id);

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
