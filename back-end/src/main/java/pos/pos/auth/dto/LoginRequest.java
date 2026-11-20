package pos.pos.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @JsonAlias("email")
    @NotBlank(message = "Identifier is required")
    @Size(max = 150, message = "Identifier must be at most 150 characters")
    private String identifier;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;

    // At the device limit: the signed-in device the person chose to sign out so this one can sign in (user, 2026-11-18).
    private java.util.UUID signOutSessionId;
}
