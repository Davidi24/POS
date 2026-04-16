package pos.pos.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordWithCodeRequest {

    @NotBlank
    @Size(max = 50, message = "Phone must be at most 50 characters")
    private String phone;

    @NotBlank
    @Size(min = 4, max = 10)
    private String code;

    @NotBlank
    @Size(min = 8, max = 100)
    private String newPassword;
}
