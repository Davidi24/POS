package pos.pos.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import pos.pos.user.dto.UserResponse;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String accessToken;
    @JsonIgnore
    private String refreshToken;
    private String tokenType;
    private Long expiresIn;
    private UserResponse user;
}
