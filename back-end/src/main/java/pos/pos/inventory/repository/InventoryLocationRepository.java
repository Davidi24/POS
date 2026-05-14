package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.inventory.entity.InventoryLocation;

import java.util.UUID;

public interface InventoryLocationRepository extends JpaRepository<InventoryLocation, UUID> {
}
