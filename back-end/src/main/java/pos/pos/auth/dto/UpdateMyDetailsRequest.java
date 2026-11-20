package pos.pos.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Profile → My details: what a person changes about themselves (user, 2026-11-18). */
@Getter
@Setter
public class UpdateMyDetailsRequest {

    @NotBlank(message = "Your first name is needed")
    @Size(max = 100, message = "First name must be at most 100 characters")
    private String firstName;

    @NotBlank(message = "Your last name is needed")
    @Size(max = 100, message = "Last name must be at most 100 characters")
    private String lastName;

    @Size(max = 50, message = "Phone must be at most 50 characters")
    private String phone;
}
