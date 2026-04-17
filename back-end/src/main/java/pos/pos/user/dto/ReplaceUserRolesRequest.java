package pos.pos.user.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class ReplaceUserRolesRequest {

    @NotEmpty(message = "At least one role id is required")
    private Set<@NotNull(message = "Role id is required") UUID> roleIds;
}
