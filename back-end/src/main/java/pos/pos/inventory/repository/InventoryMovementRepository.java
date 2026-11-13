package pos.pos.inventory.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.inventory.entity.InventoryMovement;
import pos.pos.inventory.enums.InventoryMovementType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {

    boolean existsByOrderLineItem_IdAndMovementType(UUID orderLineItemId, InventoryMovementType movementType);

    Optional<InventoryMovement> findByIdAndInventoryItem_Restaurant_Id(UUID id, UUID restaurantId);

    // Backs GET /inventory/movements. orderLineItemId, movementType, and itemId are all optional
    // and combine with AND semantics -- whichever ones are non-null narrow the result together.
    // A single query like this is what lets every filter combination (including none, and all
    // three at once) resolve to one predictable list, instead of juggling several endpoints that
    // could collide with each other when more than one query param was supplied.
    @Query("""
            SELECT mv FROM InventoryMovement mv
            WHERE mv.inventoryItem.restaurant.id = :restaurantId
              AND (:orderLineItemId IS NULL OR mv.orderLineItem.id = :orderLineItemId)
              AND (:movementType IS NULL OR mv.movementType = :movementType)
              AND (:itemId IS NULL OR mv.inventoryItem.id = :itemId)
              AND (:createdBy IS NULL OR mv.createdBy = :createdBy)
              AND mv.occurredAt >= :from AND mv.occurredAt < :to
            ORDER BY mv.occurredAt DESC, mv.id DESC
            """)
    List<InventoryMovement> search(
            @Param("restaurantId") UUID restaurantId,
            @Param("orderLineItemId") UUID orderLineItemId,
            @Param("movementType") InventoryMovementType movementType,
            @Param("itemId") UUID itemId,
            @Param("createdBy") UUID createdBy,
            // History → day filter: that day in the restaurant's time zone; otherwise all of time.
            @Param("from") java.time.OffsetDateTime from,
            @Param("to") java.time.OffsetDateTime to,
            Pageable pageable
    );

    // History → person filter: who made changes (for one item when [itemId] is set), with how many, most first.
    // Rows are [user id, first name, last name, username, count].
    @Query("""
            SELECT u.id, u.firstName, u.lastName, u.username, COUNT(mv) FROM InventoryMovement mv JOIN mv.createdByUser u
            WHERE mv.inventoryItem.restaurant.id = :restaurantId
              AND (:itemId IS NULL OR mv.inventoryItem.id = :itemId)
            GROUP BY u.id, u.firstName, u.lastName, u.username
            ORDER BY COUNT(mv) DESC
            """)
    List<Object[]> people(@Param("restaurantId") UUID restaurantId, @Param("itemId") UUID itemId);

    // Backs GET /inventory/activity: the cost of every kind of change per day (in the restaurant's time zone) since
    // :from. Rows are [day as YYYY-MM-DD, movement type, summed total_cost_delta, number of changes].
    @Query(value = """
            SELECT to_char(mv.occurred_at AT TIME ZONE :zone, 'YYYY-MM-DD') AS day,
                   mv.movement_type AS type,
                   COALESCE(SUM(mv.total_cost_delta), 0) AS cost,
                   COUNT(*) AS changes
            FROM {h-schema}inventory_movements mv
            JOIN {h-schema}inventory_items item ON item.id = mv.inventory_item_id
            WHERE item.restaurant_id = :restaurantId AND mv.occurred_at >= :from
            GROUP BY 1, 2
            """, nativeQuery = true)
    List<Object[]> dailyCostByType(
            @Param("restaurantId") UUID restaurantId,
            @Param("zone") String zone,
            @Param("from") java.time.OffsetDateTime from
    );
}
