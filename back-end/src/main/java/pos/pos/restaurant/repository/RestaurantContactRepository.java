package pos.pos.restaurant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.restaurant.entity.RestaurantContact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantContactRepository extends JpaRepository<RestaurantContact, UUID> {

    List<RestaurantContact> findAllByRestaurantIdAndDeletedAtIsNullOrderByIsPrimaryDescCreatedAtAsc(UUID restaurantId);

    Optional<RestaurantContact> findByIdAndRestaurantIdAndDeletedAtIsNull(UUID id, UUID restaurantId);

    boolean existsByRestaurantIdAndIsPrimaryTrueAndDeletedAtIsNull(UUID restaurantId);

    Optional<RestaurantContact> findByRestaurantIdAndIsPrimaryTrueAndDeletedAtIsNull(UUID restaurantId);

    // Clears all primary flags for a restaurant in one UPDATE to avoid the select→update race condition.
    @Modifying
    @Query("""
            UPDATE RestaurantContact c
            SET c.isPrimary = false, c.updatedBy = :actorId
            WHERE c.restaurant.id = :restaurantId
              AND c.isPrimary = true
              AND c.deletedAt IS NULL
            """)
    void clearAllPrimary(
            @Param("restaurantId") UUID restaurantId,
            @Param("actorId") UUID actorId
    );
}
