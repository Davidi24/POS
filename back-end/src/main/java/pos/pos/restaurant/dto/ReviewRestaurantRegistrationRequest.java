package pos.pos.restaurant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import pos.pos.restaurant.enums.RestaurantRegistrationDecision;

@Data
public class ReviewRestaurantRegistrationRequest {

    @NotNull(message = "decision is required")
    private RestaurantRegistrationDecision decision;
}
