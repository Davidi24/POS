package pos.pos.security.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.security.cookie")
@Validated
public class AuthCookieProperties {

    @NotBlank
    private String refreshTokenName;

    @NotBlank
    private String refreshTokenPath;

    @NotBlank
    private String sameSite;

    @NotNull
    private Boolean secure;

    private String domain;
}
