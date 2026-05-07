package pos.pos.config.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.device.pairing")
@Getter
@Setter
@Validated
public class DevicePairingProperties {

    @NotBlank
    private String tokenPepper;

    @NotNull
    private Duration defaultTtl;

    @NotNull
    private Duration maxTtl;

    @AssertTrue(message = "tokenPepper must be at least 32 characters")
    public boolean isTokenPepperStrongEnough() {
        return tokenPepper == null || tokenPepper.length() >= 32;
    }

    @AssertTrue(message = "defaultTtl must be greater than zero")
    public boolean isDefaultTtlPositive() {
        return defaultTtl == null || (!defaultTtl.isZero() && !defaultTtl.isNegative());
    }

    @AssertTrue(message = "maxTtl must be greater than zero")
    public boolean isMaxTtlPositive() {
        return maxTtl == null || (!maxTtl.isZero() && !maxTtl.isNegative());
    }

    @AssertTrue(message = "maxTtl must be greater than or equal to defaultTtl")
    public boolean isTtlWindowValid() {
        return defaultTtl == null || maxTtl == null || !maxTtl.minus(defaultTtl).isNegative();
    }
}
