package pos.pos.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Profile → change email: the new address (confirmed by a link sent there) and the current password. */
@Getter
@Setter
public class ChangeMyEmailRequest {

    @NotBlank(message = "Type the new email")
    @Email(message = "That doesn't look like an email address")
    @Size(max = 150, message = "Email must be at most 150 characters")
    private String newEmail;

    @NotBlank(message = "Type your current password")
    @Size(max = 100, message = "Password must be at most 100 characters")
    private String currentPassword;
}
