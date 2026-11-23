package pos.pos.restaurant.receipt;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RestaurantReceiptDesignRepository extends JpaRepository<RestaurantReceiptDesign, UUID> {
}
