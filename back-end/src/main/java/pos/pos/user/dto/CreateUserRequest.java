package pos.pos.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import pos.pos.auth.enums.ClientLinkTarget;

import java.util.UUID;

@Data
public class CreateUserRequest {

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(
            regexp = "^[A-Za-z0-9._-]+$",
            message = "Username may only contain letters, numbers, dots, underscores, and hyphens"
    )
    private String username;

    @NotBlank(message = "Temporary password is required")
    @Size(min = 8, max = 100, message = "Temporary password must be between 8 and 100 characters")
    private String temporaryPassword;

    @NotBlank(message = "First name is required")
    @Size(max = 50, message = "First name must be at most 50 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 50, message = "Last name must be at most 50 characters")
    private String lastName;

    @Size(max = 30, message = "Phone must be at most 30 characters")
    private String phone;

    private ClientLinkTarget clientTarget = ClientLinkTarget.UNIVERSAL;

    @NotNull(message = "Role id is required")
    private UUID roleId;
}
