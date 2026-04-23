package pos.pos.restaurant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.restaurant.entity.RestaurantAddress;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantAddressRepository extends JpaRepository<RestaurantAddress, UUID> {

    List<RestaurantAddress> findAllByRestaurantIdAndDeletedAtIsNullOrderByIsPrimaryDescCreatedAtAsc(UUID restaurantId);

    Optional<RestaurantAddress> findByIdAndRestaurantIdAndDeletedAtIsNull(UUID id, UUID restaurantId);

    Optional<RestaurantAddress> findByRestaurantIdAndIsPrimaryTrueAndDeletedAtIsNull(UUID restaurantId);
}
