package pos.pos.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

// Everything one person should be able to do; the difference from their roles is stored for them alone.
@Data
public class ReplacePersonPermissionsRequest {

    @NotNull(message = "permissionCodes is required")
    @Size(max = 200, message = "permissionCodes can have at most 200 values")
    private Set<@NotBlank(message = "Permission code is required") @Size(max = 100) String> permissionCodes;
}
