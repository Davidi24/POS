package pos.pos.auth.dto;

import lombok.Builder;
import lombok.Getter;
import pos.pos.user.dto.UserResponse;

@Getter
@Builder
public class DeviceLoginResponse {

    private final String accessToken;
    private final String refreshToken;
    private final String tokenType;
    private final Long expiresIn;
    private final UserResponse user;
}
