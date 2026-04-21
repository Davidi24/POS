package pos.pos.auth.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WebAuthenticationResponse {

    private final String accessToken;
    private final String tokenType;
    private final Long expiresIn;
    private final CurrentUserResponse user;
}
