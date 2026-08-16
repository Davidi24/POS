package pos.pos.inventory.mapper;

import org.springframework.stereotype.Component;
import pos.pos.inventory.dto.InventoryMovementResponse;
import pos.pos.inventory.entity.InventoryMovement;
import pos.pos.user.entity.User;

@Component
public class InventoryMovementMapper {

    public InventoryMovementResponse toResponse(InventoryMovement movement) {
        if (movement == null) {
            return null;
        }

        return InventoryMovementResponse.builder()
                .id(movement.getId())
                .locationId(movement.getLocation() == null ? null : movement.getLocation().getId())
                .locationName(movement.getLocation() == null ? null : movement.getLocation().getName())
                .inventoryItemId(movement.getInventoryItem() == null ? null : movement.getInventoryItem().getId())
                .inventoryItemName(movement.getInventoryItem() == null ? null : movement.getInventoryItem().getName())
                .orderLineItemId(movement.getOrderLineItem() == null ? null : movement.getOrderLineItem().getId())
                .movementType(movement.getMovementType())
                .quantityDelta(movement.getQuantityDelta())
                .unit(movement.getUnit())
                .unitCostSnapshot(movement.getUnitCostSnapshot())
                .totalCostDelta(movement.getTotalCostDelta())
                .reason(movement.getReason())
                .referenceType(movement.getReferenceType())
                .referenceId(movement.getReferenceId())
                .occurredAt(movement.getOccurredAt())
                .createdByUserName(displayName(movement.getCreatedByUser()))
                .createdAt(movement.getCreatedAt())
                .build();
    }

    private String displayName(User user) {
        if (user == null) {
            return null;
        }

        String firstName = user.getFirstName();
        String lastName = user.getLastName();
        if (firstName == null && lastName == null) {
            return null;
        }
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null) {
            return firstName;
        }
        return firstName + " " + lastName;
    }
}
