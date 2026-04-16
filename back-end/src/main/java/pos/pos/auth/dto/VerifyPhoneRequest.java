package pos.pos.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerifyPhoneRequest {

    @NotBlank
    @Size(min = 4, max = 10)
    private String code;
}
