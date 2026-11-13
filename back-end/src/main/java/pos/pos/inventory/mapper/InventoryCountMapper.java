package pos.pos.inventory.mapper;

import org.springframework.stereotype.Component;
import pos.pos.inventory.dto.InventoryCountLineResponse;
import pos.pos.inventory.dto.InventoryCountResponse;
import pos.pos.inventory.entity.InventoryCount;
import pos.pos.inventory.entity.InventoryCountLine;
import pos.pos.user.entity.User;

import java.util.List;

@Component
public class InventoryCountMapper {

    public InventoryCountResponse toResponse(InventoryCount count) {
        if (count == null) {
            return null;
        }

        List<InventoryCountLineResponse> lines = count.getLines() == null
                ? List.of()
                : count.getLines().stream().map(this::lineToResponse).toList();

        return InventoryCountResponse.builder()
                .id(count.getId())
                .restaurantId(count.getRestaurant() == null ? null : count.getRestaurant().getId())
                .branchId(count.getBranch() == null ? null : count.getBranch().getId())
                .locationId(count.getLocation() == null ? null : count.getLocation().getId())
                .locationName(count.getLocation() == null ? null : count.getLocation().getName())
                .countNumber(count.getCountNumber())
                .status(count.getStatus())
                .scheduledAt(count.getScheduledAt())
                .completedAt(count.getCompletedAt())
                .approvedByUserName(displayName(count.getApprovedByUser()))
                .approvedAt(count.getApprovedAt())
                .varianceValue(count.getVarianceValue())
                .notes(count.getNotes())
                .createdByUserName(displayName(count.getCreatedByUser()))
                .updatedByUserName(displayName(count.getUpdatedByUser()))
                .createdAt(count.getCreatedAt())
                .updatedAt(count.getUpdatedAt())
                .lines(lines)
                .build();
    }

    public InventoryCountLineResponse lineToResponse(InventoryCountLine line) {
        if (line == null) {
            return null;
        }

        return InventoryCountLineResponse.builder()
                .id(line.getId())
                .inventoryItemId(line.getInventoryItem() == null ? null : line.getInventoryItem().getId())
                .itemNameSnapshot(line.getItemNameSnapshot())
                .expectedQuantity(line.getExpectedQuantity())
                .countedQuantity(line.getCountedQuantity())
                .varianceQuantity(line.getVarianceQuantity())
                .unit(line.getUnit())
                .unitCostSnapshot(line.getUnitCostSnapshot())
                .varianceValue(line.getVarianceValue())
                .notes(line.getNotes())
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
