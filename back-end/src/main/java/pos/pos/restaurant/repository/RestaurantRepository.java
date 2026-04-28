package pos.pos.restaurant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.restaurant.entity.Restaurant;

import java.util.Optional;
import java.util.UUID;

public interface RestaurantRepository extends JpaRepository<Restaurant, UUID> {

    Optional<Restaurant> findByIdAndDeletedAtIsNull(UUID id);
}
