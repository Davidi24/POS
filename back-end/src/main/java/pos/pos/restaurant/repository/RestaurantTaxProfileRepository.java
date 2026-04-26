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

    // Atomically clears the default flag on all tax profiles for a restaurant, optionally keeping one.
    @Modifying
    @Query("""
            UPDATE RestaurantTaxProfile t
            SET t.isDefault = false, t.updatedBy = :actorId
            WHERE t.restaurant.id = :restaurantId
              AND t.isDefault = true
              AND t.deletedAt IS NULL
              AND (:excludeId IS NULL OR t.id != :excludeId)
            """)
    void clearDefault(
            @Param("restaurantId") UUID restaurantId,
            @Param("excludeId") UUID excludeId,
            @Param("actorId") UUID actorId
    );
}
