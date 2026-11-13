package pos.pos.mail;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.mail.outbox")
@Getter
@Setter
@Validated
public class EmailOutboxProperties {

    /** Emails picked up per check. */
    @Min(1)
    private int batchSize = 20;

    /** Tries before an email is given up (with waits of 1, 3, 9, 27 … minutes, at most 2 hours). */
    @Min(1)
    private int maxAttempts = 8;

    /** How long a server may hold an email it's sending before another server takes it over. */
    @NotNull
    private Duration sendingLease = Duration.ofMinutes(2);

    /** Sent, failed and replaced emails are deleted after this long. */
    @NotNull
    private Duration retention = Duration.ofDays(90);
}
