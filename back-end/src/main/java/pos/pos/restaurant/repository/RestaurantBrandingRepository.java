package pos.pos.restaurant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.restaurant.entity.RestaurantBranding;

import java.util.Optional;
import java.util.UUID;

public interface RestaurantBrandingRepository extends JpaRepository<RestaurantBranding, UUID> {

    Optional<RestaurantBranding> findByRestaurantIdAndDeletedAtIsNull(UUID restaurantId);
}
