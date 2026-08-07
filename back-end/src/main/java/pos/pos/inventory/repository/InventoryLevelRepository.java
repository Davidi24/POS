package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.inventory.entity.InventoryLevel;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, UUID> {

    Optional<InventoryLevel> findByLocation_IdAndInventoryItem_Id(UUID locationId, UUID itemId);

    List<InventoryLevel> findAllByLocation_IdOrderByInventoryItem_NameAsc(UUID locationId);

    List<InventoryLevel> findAllByInventoryItem_IdOrderByLocation_NameAsc(UUID itemId);

    @Query("""
            SELECT lvl FROM InventoryLevel lvl
            WHERE lvl.location.restaurant.id = :restaurantId
              AND lvl.reorderQuantity IS NOT NULL
              AND lvl.onHandQuantity < lvl.reorderQuantity
            ORDER BY lvl.inventoryItem.name ASC
            """)
    List<InventoryLevel> findLowStockByRestaurantId(@Param("restaurantId") UUID restaurantId);
}
