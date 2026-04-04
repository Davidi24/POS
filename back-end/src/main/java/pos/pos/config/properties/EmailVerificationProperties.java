package pos.pos.config.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "app.auth.email-verification")
@Getter
@Setter
@Validated
public class EmailVerificationProperties {

    private Duration tokenTtl = Duration.ofHours(24);
    private Duration requestCooldown = Duration.ofMinutes(5);

    @NotBlank
    private String tokenPepper = "change-this-local-dev-email-verification-pepper";

    @NotBlank
    private String verifyPath = "/verify-email";

    @NotBlank
    private String subject = "Verify your POS email";
}
