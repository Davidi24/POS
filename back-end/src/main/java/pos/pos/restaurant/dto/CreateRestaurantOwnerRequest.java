package pos.pos.restaurant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.auth.enums.ClientLinkTarget;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRestaurantOwnerRequest {

    @Email(message = "email must be a valid email")
    @NotBlank(message = "email is required")
    private String email;

    @NotBlank(message = "username is required")
    @Size(min = 3, max = 50, message = "username must be between 3 and 50 characters")
    @Pattern(
            regexp = "^[A-Za-z0-9._-]+$",
            message = "username may only contain letters, numbers, dots, underscores, and hyphens"
    )
    private String username;

    @NotBlank(message = "firstName is required")
    @Size(max = 50, message = "firstName must be at most 50 characters")
    private String firstName;

    @NotBlank(message = "lastName is required")
    @Size(max = 50, message = "lastName must be at most 50 characters")
    private String lastName;

    @Size(max = 30, message = "phone must be at most 30 characters")
    private String phone;

    @Builder.Default
    private ClientLinkTarget clientTarget = ClientLinkTarget.UNIVERSAL;
}
