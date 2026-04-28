package pos.pos.restaurant.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import pos.pos.restaurant.enums.RestaurantRegistrationDecision;

@Data
public class ReviewRestaurantRegistrationRequest {

    @NotNull(message = "decision is required")
    private RestaurantRegistrationDecision decision;

    @Size(max = 500, message = "rejectionReason must be at most 500 characters")
    private String rejectionReason;
}
