package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.inventory.entity.InventoryCount;

import java.util.UUID;

public interface InventoryCountRepository extends JpaRepository<InventoryCount, UUID> {
}
