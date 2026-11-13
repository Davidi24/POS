package pos.pos.restaurant.currency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RestaurantCurrencyChangeRepository extends JpaRepository<RestaurantCurrencyChange, UUID> {

    List<RestaurantCurrencyChange> findAllByRestaurantIdOrderByChangedAtDesc(UUID restaurantId);
}
