package pos.pos.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank
    @Size(max = 4096, message = "token must be at most 4096 characters")
    private String token;

    @NotBlank
    @Size(min = 8, max = 100)
    private String newPassword;
}
