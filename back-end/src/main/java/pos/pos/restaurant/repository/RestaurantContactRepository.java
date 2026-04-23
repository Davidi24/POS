package pos.pos.restaurant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.restaurant.entity.RestaurantContact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RestaurantContactRepository extends JpaRepository<RestaurantContact, UUID> {

    List<RestaurantContact> findAllByRestaurantIdAndDeletedAtIsNullOrderByIsPrimaryDescCreatedAtAsc(UUID restaurantId);

    Optional<RestaurantContact> findByIdAndRestaurantIdAndDeletedAtIsNull(UUID id, UUID restaurantId);

    Optional<RestaurantContact> findByRestaurantIdAndIsPrimaryTrueAndDeletedAtIsNull(UUID restaurantId);
}
