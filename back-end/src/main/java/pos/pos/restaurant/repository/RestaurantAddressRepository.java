package pos.pos.restaurant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.restaurant.entity.RestaurantAddress;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantAddressRepository extends JpaRepository<RestaurantAddress, UUID> {

    List<RestaurantAddress> findAllByRestaurantIdAndDeletedAtIsNullOrderByIsPrimaryDescCreatedAtAsc(UUID restaurantId);

    Optional<RestaurantAddress> findByIdAndRestaurantIdAndDeletedAtIsNull(UUID id, UUID restaurantId);

    boolean existsByRestaurantIdAndDeletedAtIsNull(UUID restaurantId);

    Optional<RestaurantAddress> findByRestaurantIdAndIsPrimaryTrueAndDeletedAtIsNull(UUID restaurantId);

    // Atomically clears the primary flag on all addresses for a restaurant, optionally keeping one.
    // Runs as a single UPDATE to avoid the select→update race condition.
    @Modifying
    @Query("""
            UPDATE RestaurantAddress a
            SET a.isPrimary = false, a.updatedBy = :actorId
            WHERE a.restaurant.id = :restaurantId
              AND a.isPrimary = true
              AND a.deletedAt IS NULL
              AND (:excludeId IS NULL OR a.id != :excludeId)
            """)
    void clearPrimary(
            @Param("restaurantId") UUID restaurantId,
            @Param("excludeId") UUID excludeId,
            @Param("actorId") UUID actorId
    );
}
