package pos.pos.role.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateRoleStatusRequest {

    @NotNull(message = "isActive is required")
    private Boolean isActive;
}
