package pos.pos.auth.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyEmailRequest {

    @NotBlank
    @Size(max = 4096, message = "token must be at most 4096 characters")
    private String token;
}
