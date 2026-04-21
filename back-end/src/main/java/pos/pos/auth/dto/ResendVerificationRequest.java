package pos.pos.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import pos.pos.auth.enums.ClientLinkTarget;

@Data
public class ResendVerificationRequest {

    @Email
    @NotBlank
    private String email;

    private ClientLinkTarget clientTarget = ClientLinkTarget.UNIVERSAL;
}
