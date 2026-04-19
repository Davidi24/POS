package pos.pos.user.dto;

import lombok.Data;
import pos.pos.auth.enums.ClientLinkTarget;
import pos.pos.auth.enums.RecoveryChannel;

@Data
public class AdminPasswordResetRequest {

    private RecoveryChannel channel = RecoveryChannel.EMAIL;
    private ClientLinkTarget clientTarget = ClientLinkTarget.UNIVERSAL;
}
