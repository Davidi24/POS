package pos.pos.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.auth.password-reset")
@Getter
@Setter
@Validated
public class PasswordResetProperties {

    @NotNull
    private Duration tokenTtl;

    // An owner's invitation stays usable longer than a reset link: they may not read their email right away.
    @NotNull
    private Duration inviteTokenTtl = Duration.ofHours(72);

    @NotNull
    private Duration requestCooldown;

    // Where emailed links point (this backend's own page). Blank: the website from app.frontend. A placeholder left
    // unresolved (PUBLIC_BASE_URL missing in production) stops the start instead of breaking every invitation later.
    @jakarta.validation.constraints.Pattern(
            regexp = "^$|^https?://[^\\s${}]+$",
            message = "must be an http(s) address; set PUBLIC_BASE_URL or PASSWORD_LINK_BASE_URL"
    )
    private String linkBaseUrl;

    @NotBlank
    private String tokenPepper;

    @NotBlank
    private String resetPath;

    @NotBlank
    private String subject;
}
