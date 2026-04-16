package pos.pos.config.properties;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import pos.pos.auth.enums.SmsDeliveryMode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "app.auth.sms")
@Getter
@Setter
@Validated
public class SmsAuthProperties {

    @NotNull
    private SmsDeliveryMode deliveryMode = SmsDeliveryMode.LOG_ONLY;

    @NotNull
    private Duration passwordResetCodeTtl = Duration.ofMinutes(10);

    @NotNull
    private Duration phoneVerificationCodeTtl = Duration.ofMinutes(10);

    @NotNull
    private Duration requestCooldown = Duration.ofMinutes(1);

    @Min(1)
    private int dailyRequestLimit = 15;

    @Min(4)
    @Max(8)
    private int codeLength = 6;

    @Min(1)
    private int maxAttempts = 5;

    @NotBlank
    private String codePepper = "change-this-local-dev-sms-code-pepper";

    private List<String> restrictedPasswordResetRoleCodes = new ArrayList<>(List.of("SUPER_ADMIN", "OWNER"));

    public boolean isEnabled() {
        return deliveryMode != SmsDeliveryMode.DISABLED;
    }
}
