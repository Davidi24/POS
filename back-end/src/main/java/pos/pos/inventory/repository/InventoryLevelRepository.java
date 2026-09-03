package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.inventory.entity.InventoryLevel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, UUID> {

    Optional<InventoryLevel> findByLocation_IdAndInventoryItem_Id(UUID locationId, UUID itemId);

    List<InventoryLevel> findAllByLocation_IdOrderByInventoryItem_NameAsc(UUID locationId);

    List<InventoryLevel> findAllByInventoryItem_IdOrderByLocation_NameAsc(UUID itemId);

    // Three-tier priority for "what counts as the reorder threshold": a manager's manual
    // override first, then whatever the system last calculated, then the item's own catalog
    // reorderPoint as a last resort. Same priority InventoryLevelMapper uses for the lowStock
    // flag on a single level, just expressed as a cross-table COALESCE here.
    @Query("""
            SELECT lvl FROM InventoryLevel lvl
            WHERE lvl.location.restaurant.id = :restaurantId
              AND COALESCE(lvl.manualReorderPoint, lvl.calculatedReorderPoint, lvl.inventoryItem.reorderPoint) IS NOT NULL
              AND lvl.onHandQuantity < COALESCE(lvl.manualReorderPoint, lvl.calculatedReorderPoint, lvl.inventoryItem.reorderPoint)
            ORDER BY lvl.inventoryItem.name ASC
            """)
    List<InventoryLevel> findLowStockByRestaurantId(@Param("restaurantId") UUID restaurantId);

    @Query("""
            SELECT lvl FROM InventoryLevel lvl
            WHERE lvl.location.restaurant.id = :restaurantId
            ORDER BY lvl.location.name ASC, lvl.inventoryItem.name ASC
            """)
    List<InventoryLevel> findAllByRestaurantId(@Param("restaurantId") UUID restaurantId);

    // SUM needs JPQL, not a derived method name. COALESCE covers the case where the item has
    // no InventoryLevel rows at all yet (no delivery/movement ever recorded for it anywhere) --
    // returns zero instead of null so callers never have to null-check the total.
    @Query("""
            SELECT COALESCE(SUM(lvl.onHandQuantity), 0) FROM InventoryLevel lvl
            WHERE lvl.location.restaurant.id = :restaurantId
              AND lvl.inventoryItem.id = :itemId
            """)
    BigDecimal sumOnHandQuantityByRestaurantAndItem(
            @Param("restaurantId") UUID restaurantId,
            @Param("itemId") UUID itemId
    );
}
