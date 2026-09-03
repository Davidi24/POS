package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.inventory.entity.InventoryUnitConversion;
import pos.pos.inventory.enums.InventoryUnit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryUnitConversionRepository extends JpaRepository<InventoryUnitConversion, UUID> {

    List<InventoryUnitConversion> findAllByInventoryItem_Id(UUID inventoryItemId);

    Optional<InventoryUnitConversion> findByInventoryItem_IdAndFromUnitAndToUnit(
            UUID inventoryItemId,
            InventoryUnit fromUnit,
            InventoryUnit toUnit
    );
}
