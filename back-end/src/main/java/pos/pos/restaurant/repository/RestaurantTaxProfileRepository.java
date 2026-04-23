package pos.pos.restaurant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.restaurant.entity.RestaurantTaxProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantTaxProfileRepository extends JpaRepository<RestaurantTaxProfile, UUID> {

    List<RestaurantTaxProfile> findAllByRestaurantIdAndDeletedAtIsNullOrderByIsDefaultDescCreatedAtAsc(UUID restaurantId);

    Optional<RestaurantTaxProfile> findByIdAndRestaurantIdAndDeletedAtIsNull(UUID id, UUID restaurantId);

    Optional<RestaurantTaxProfile> findByRestaurantIdAndIsDefaultTrueAndDeletedAtIsNull(UUID restaurantId);
}
