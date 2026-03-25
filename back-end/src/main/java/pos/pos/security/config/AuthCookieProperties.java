package pos.pos.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.cookie")
public class AuthCookieProperties {

    private String refreshTokenName = "refreshToken";
    private String refreshTokenPath = "/auth";
    private String sameSite = "Lax";
    private boolean secure = true;
    private String domain;
}