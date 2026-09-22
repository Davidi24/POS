package pos.pos.inventory.mapper;

import org.springframework.stereotype.Component;
import pos.pos.inventory.dto.InventoryLocationRequest;
import pos.pos.inventory.dto.InventoryLocationResponse;
import pos.pos.inventory.entity.InventoryLocation;
import pos.pos.user.entity.User;

@Component
public class InventoryLocationMapper {

    public void applyRequest(InventoryLocation location, InventoryLocationRequest request) {
        location.setCode(request.getCode());
        location.setName(request.getName());
        location.setLocationType(request.getLocationType());
        location.setNotes(request.getNotes());
        location.setActive(request.getActive() == null || request.getActive());
    }

    public InventoryLocationResponse toResponse(InventoryLocation location) {
        if (location == null) {
            return null;
        }

        return InventoryLocationResponse.builder()
                .id(location.getId())
                .restaurantId(location.getRestaurant() == null ? null : location.getRestaurant().getId())
                .branchId(location.getBranch() == null ? null : location.getBranch().getId())
                .code(location.getCode())
                .name(location.getName())
                .locationType(location.getLocationType())
                .notes(location.getNotes())
                .active(location.isActive())
                .createdByUserName(displayName(location.getCreatedByUser()))
                .updatedByUserName(displayName(location.getUpdatedByUser()))
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
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
