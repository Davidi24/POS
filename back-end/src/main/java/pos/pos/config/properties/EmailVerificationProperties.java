package pos.pos.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.auth.email-verification")
@Getter
@Setter
@Validated
public class EmailVerificationProperties {

    @NotNull
    private Duration tokenTtl;

    @NotNull
    private Duration requestCooldown;

    @NotBlank
    private String tokenPepper;

    @NotBlank
    private String verifyPath;

    @NotBlank
    private String subject;
}
