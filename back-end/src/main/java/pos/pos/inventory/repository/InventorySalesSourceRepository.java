package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.inventory.entity.InventorySalesSource;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventorySalesSourceRepository extends JpaRepository<InventorySalesSource, UUID> {
    @EntityGraph(attributePaths = {"branch", "inventoryItem", "location"})
    List<InventorySalesSource> findAllByRestaurant_IdOrderByBranch_NameAscInventoryItem_NameAsc(UUID restaurantId);

    Optional<InventorySalesSource> findByBranch_IdAndInventoryItem_Id(UUID branchId, UUID inventoryItemId);

    void deleteByBranch_IdAndInventoryItem_Id(UUID branchId, UUID inventoryItemId);

    boolean existsByLocation_Id(UUID locationId);

    boolean existsByInventoryItem_Id(UUID inventoryItemId);
}
