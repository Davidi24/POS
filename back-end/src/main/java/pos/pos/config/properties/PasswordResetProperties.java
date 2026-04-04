package pos.pos.config.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.auth.password-reset")
@Getter
@Setter
@Validated
public class PasswordResetProperties {

    private Duration tokenTtl = Duration.ofMinutes(30);
    private Duration requestCooldown = Duration.ofMinutes(2);

    @NotBlank
    private String tokenPepper = "change-this-local-dev-password-reset-pepper";

    @NotBlank
    private String resetPath = "/reset-password";

    @NotBlank
    private String subject = "Reset your POS password";
}
