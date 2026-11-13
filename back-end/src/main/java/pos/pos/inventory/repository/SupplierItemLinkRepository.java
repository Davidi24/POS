package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.inventory.entity.SupplierItemLink;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierItemLinkRepository extends JpaRepository<SupplierItemLink, UUID> {

    List<SupplierItemLink> findAllBySupplierIdIn(Collection<UUID> supplierIds);

    Optional<SupplierItemLink> findBySupplierIdAndInventoryItemId(UUID supplierId, UUID inventoryItemId);
}
