package pos.pos.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pos.pos.inventory.entity.InventoryMovement;
import pos.pos.inventory.enums.InventoryMovementType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {

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
            ORDER BY mv.occurredAt DESC
            """)
    List<InventoryMovement> search(
            @Param("restaurantId") UUID restaurantId,
            @Param("orderLineItemId") UUID orderLineItemId,
            @Param("movementType") InventoryMovementType movementType,
            @Param("itemId") UUID itemId
    );
}
