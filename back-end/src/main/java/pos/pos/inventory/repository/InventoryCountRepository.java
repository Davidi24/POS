package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.inventory.entity.InventoryCount;
import pos.pos.inventory.enums.InventoryCountStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryCountRepository extends JpaRepository<InventoryCount, UUID> {

    Optional<InventoryCount> findByIdAndRestaurant_Id(UUID id, UUID restaurantId);

    List<InventoryCount> findAllByRestaurant_IdOrderByCreatedAtDesc(UUID restaurantId);

    List<InventoryCount> findAllByRestaurant_IdAndStatusOrderByCreatedAtDesc(
            UUID restaurantId,
            InventoryCountStatus status
    );
}
