package pos.pos.role.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class ReplaceRolePermissionsRequest {

    @NotNull(message = "permissionIds is required")
    private Set<@NotNull(message = "Permission id is required") UUID> permissionIds;
}
