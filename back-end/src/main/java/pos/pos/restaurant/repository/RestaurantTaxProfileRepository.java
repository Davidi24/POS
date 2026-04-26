package pos.pos.restaurant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.restaurant.entity.RestaurantTaxProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantTaxProfileRepository extends JpaRepository<RestaurantTaxProfile, UUID> {

    List<RestaurantTaxProfile> findAllByRestaurantIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtAsc(UUID restaurantId);

    Optional<RestaurantTaxProfile> findByIdAndRestaurantIdAndDeletedAtIsNull(UUID id, UUID restaurantId);

    boolean existsByRestaurantIdAndIsDefaultTrueAndDeletedAtIsNull(UUID restaurantId);

    Optional<RestaurantTaxProfile> findByRestaurantIdAndIsDefaultTrueAndDeletedAtIsNull(UUID restaurantId);

    // Clears all default flags for a restaurant in one UPDATE to avoid the select→update race condition.
    @Modifying
    @Query("""
            UPDATE RestaurantTaxProfile t
            SET t.isDefault = false, t.updatedBy = :actorId
            WHERE t.restaurant.id = :restaurantId
              AND t.isDefault = true
              AND t.deletedAt IS NULL
            """)
    void clearAllDefault(
            @Param("restaurantId") UUID restaurantId,
            @Param("actorId") UUID actorId
    );
}
