package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.inventory.entity.InventoryItem;

import java.util.UUID;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {
}
