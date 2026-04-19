package pos.pos.user.dto;

import lombok.Data;
import pos.pos.auth.enums.ClientLinkTarget;

@Data
public class ClientTargetRequest {

    private ClientLinkTarget clientTarget = ClientLinkTarget.UNIVERSAL;
}
