package pos.pos.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;

// The permissions left ticked for one Co-Super-Admin; everything else is switched off for them.
@Data
public class ReplaceCoSuperAdminPermissionsRequest {

    @NotNull(message = "grantedCodes is required")
    @Size(max = 200, message = "grantedCodes can have at most 200 values")
    private Set<@NotBlank(message = "Permission code is required") @Size(max = 100) String> grantedCodes;
}
