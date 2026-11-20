package pos.pos.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoleRequest {

    @NotBlank(message = "Role name is required")
    @Size(max = 100, message = "Role name must be at most 100 characters")
    private String name;

    @Size(max = 1000, message = "Description must be at most 1000 characters")
    private String description;

    // One of the app's role pictures, by name.
    @Pattern(regexp = "[a-z0-9_]{1,40}", message = "Choose a picture")
    private String picture;

    // Whether its people get paid here (Salary and shifts show only them), and their Salary team.
    private Boolean paid;

    @Pattern(regexp = "MANAGERS|KITCHEN|WAITERS|CLEANING|EXTRA", message = "Choose a Salary team")
    private String payTeam;
}
