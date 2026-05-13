package pos.pos.menu.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateOptionItemAvailabilityRequest {

    @NotNull(message = "available is required")
    private Boolean available;
}
