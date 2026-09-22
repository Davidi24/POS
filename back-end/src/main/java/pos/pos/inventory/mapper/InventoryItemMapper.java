package pos.pos.inventory.mapper;

import org.springframework.stereotype.Component;
import pos.pos.inventory.dto.InventoryItemRequest;
import pos.pos.inventory.dto.InventoryItemResponse;
import pos.pos.inventory.entity.InventoryItem;
import pos.pos.user.entity.User;

@Component
public class InventoryItemMapper {

    public void applyRequest(InventoryItem item, InventoryItemRequest request) {
        item.setCode(request.getCode());
        item.setName(request.getName());
        item.setDescription(request.getDescription());
        item.setItemType(request.getItemType());
        item.setBaseUnit(request.getBaseUnit());
        item.setBarcode(request.getBarcode());
        item.setSupplierName(request.getSupplierName());
        item.setSupplierSku(request.getSupplierSku());
        item.setCostPerUnit(request.getCostPerUnit());
        item.setReorderPoint(request.getReorderPoint());
        item.setParLevel(request.getParLevel());
        item.setTrackInventory(request.getTrackInventory() == null || request.getTrackInventory());
        item.setActive(request.getActive() == null || request.getActive());
        item.setStorageNotes(request.getStorageNotes());
    }

    public InventoryItemResponse toResponse(InventoryItem item) {
        if (item == null) {
            return null;
        }

        return InventoryItemResponse.builder()
                .id(item.getId())
                .restaurantId(item.getRestaurant() == null ? null : item.getRestaurant().getId())
                .code(item.getCode())
                .name(item.getName())
                .description(item.getDescription())
                .itemType(item.getItemType())
                .baseUnit(item.getBaseUnit())
                .barcode(item.getBarcode())
                .supplierName(item.getSupplierName())
                .supplierSku(item.getSupplierSku())
                .costPerUnit(item.getCostPerUnit())
                .reorderPoint(item.getReorderPoint())
                .parLevel(item.getParLevel())
                .trackInventory(item.isTrackInventory())
                .active(item.isActive())
                .storageNotes(item.getStorageNotes())
                .createdByUserName(displayName(item.getCreatedByUser()))
                .updatedByUserName(displayName(item.getUpdatedByUser()))
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
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
