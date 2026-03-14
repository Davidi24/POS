package pos.pos.auth.dto;

import lombok.Data;

@Data
public class VerifyEmailRequest {

    private String token;
}
