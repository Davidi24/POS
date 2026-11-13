package pos.pos.role.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Whether a role's people get paid in this restaurant, and the Salary team they are paid in. */
public record RolePayRequest(
        @NotNull(message = "Say whether the role is paid") Boolean paid,
        @Pattern(regexp = "MANAGERS|KITCHEN|WAITERS|CLEANING|EXTRA", message = "Choose a Salary team") String payTeam
) {}
