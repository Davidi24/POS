package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.inventory.entity.InventoryLevel;

import java.util.UUID;

public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, UUID> {
}
