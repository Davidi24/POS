package pos.pos.restaurant.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import pos.pos.restaurant.enums.BranchStatus;

@Data
public class UpdateBranchStatusRequest {

    @NotNull(message = "isActive is required")
    private Boolean isActive;

    @NotNull(message = "status is required")
    private BranchStatus status;
}
