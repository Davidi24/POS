package pos.pos.restaurant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import pos.pos.restaurant.enums.RestaurantStatus;

@Data
public class UpdateRestaurantStatusRequest {

    @NotNull(message = "isActive is required")
    private Boolean isActive;

    @NotNull(message = "status is required")
    private RestaurantStatus status;
}
