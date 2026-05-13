package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.inventory.entity.InventoryMovement;

import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {
}
