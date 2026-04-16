package pos.pos.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;
import pos.pos.auth.enums.ClientLinkTarget;
import pos.pos.auth.enums.RecoveryChannel;

@Data
public class ForgotPasswordRequest {

    @Email
    private String email;

    @Size(max = 50, message = "Phone must be at most 50 characters")
    private String phone;

    private RecoveryChannel channel = RecoveryChannel.EMAIL;

    private ClientLinkTarget clientTarget = ClientLinkTarget.UNIVERSAL;
}
