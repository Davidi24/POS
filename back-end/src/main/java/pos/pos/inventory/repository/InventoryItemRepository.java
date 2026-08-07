package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.inventory.entity.InventoryItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    Optional<InventoryItem> findByIdAndRestaurant_IdAndDeletedAtIsNull(UUID id, UUID restaurantId);

    Optional<InventoryItem> findByRestaurant_IdAndBarcodeAndDeletedAtIsNull(UUID restaurantId, String barcode);

    List<InventoryItem> findAllByRestaurant_IdAndActiveTrueAndDeletedAtIsNullOrderByNameAsc(UUID restaurantId);

    List<InventoryItem> findAllByRestaurant_IdAndNameContainingIgnoreCaseAndDeletedAtIsNullOrderByNameAsc(
            UUID restaurantId,
            String keyword
    );
}
