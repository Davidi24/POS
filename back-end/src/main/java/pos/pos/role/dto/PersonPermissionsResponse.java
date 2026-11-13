package pos.pos.role.dto;

import java.util.List;

/**
 * One person's permissions: what their roles give them in their restaurant ([fromRoles]) and what they actually have
 * ([permissions]: those, less what's switched off for them, plus what's given to them alone).
 */
public record PersonPermissionsResponse(List<String> fromRoles, List<String> permissions) {
}
