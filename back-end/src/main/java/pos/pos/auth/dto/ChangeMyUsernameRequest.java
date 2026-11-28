package pos.pos.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Profile → change username: the new one and the current password. */
@Getter
@Setter
public class ChangeMyUsernameRequest {

    @NotBlank(message = "Type the new username")
    @Size(max = 50, message = "Username must be at most 50 characters")
    private String username;

    @NotBlank(message = "Type your current password")
    @Size(max = 100, message = "Password must be at most 100 characters")
    private String currentPassword;
}
