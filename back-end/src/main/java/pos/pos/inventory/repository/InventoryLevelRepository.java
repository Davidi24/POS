package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.inventory.entity.InventoryLevel;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryLevelRepository extends JpaRepository<InventoryLevel, UUID> {

    /** Apply a stock movement without a read/modify/write race (including first movement for a new level). */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO inventory_levels (
                id, location_id, inventory_item_id, on_hand_quantity, committed_quantity,
                created_at, updated_at, last_movement_at
            ) SELECT
                :id, :locationId, :itemId, GREATEST(:quantityDelta, 0), 0,
                :occurredAt, :occurredAt, :occurredAt
            WHERE :quantityDelta >= 0 OR EXISTS (
                SELECT 1 FROM inventory_levels
                WHERE location_id = :locationId AND inventory_item_id = :itemId
            )
            ON CONFLICT (location_id, inventory_item_id) DO UPDATE
            SET on_hand_quantity = inventory_levels.on_hand_quantity + :quantityDelta,
                last_movement_at = EXCLUDED.last_movement_at,
                updated_at = EXCLUDED.updated_at
            WHERE inventory_levels.on_hand_quantity + :quantityDelta >= 0
            """, nativeQuery = true)
    int applyMovementDelta(
            @Param("id") UUID id,
            @Param("locationId") UUID locationId,
            @Param("itemId") UUID itemId,
            @Param("quantityDelta") BigDecimal quantityDelta,
            @Param("occurredAt") java.time.OffsetDateTime occurredAt
    );

    Optional<InventoryLevel> findByLocation_IdAndInventoryItem_Id(UUID locationId, UUID itemId);

    List<InventoryLevel> findAllByLocation_IdOrderByInventoryItem_NameAsc(UUID locationId);

    List<InventoryLevel> findAllByInventoryItem_IdOrderByLocation_NameAsc(UUID itemId);

    @Query("""
            SELECT lvl FROM InventoryLevel lvl
            WHERE lvl.location.restaurant.id = :restaurantId
              AND lvl.inventoryItem.active = true
              AND lvl.inventoryItem.trackInventory = true
              AND lvl.location.active = true
              AND COALESCE(lvl.reorderQuantity, lvl.inventoryItem.reorderPoint) IS NOT NULL
              AND lvl.onHandQuantity < COALESCE(lvl.reorderQuantity, lvl.inventoryItem.reorderPoint)
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
