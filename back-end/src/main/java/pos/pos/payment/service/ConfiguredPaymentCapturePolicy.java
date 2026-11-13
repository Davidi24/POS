package pos.pos.payment.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pos.pos.exception.auth.AuthException;
import pos.pos.payment.enums.PaymentMethod;

import java.util.Locale;

/** Allows cash recording and an explicit non-production simulator; non-cash capture needs a trusted provider adapter. */
@Component
public class ConfiguredPaymentCapturePolicy implements PaymentCapturePolicy {

    private final Environment environment;
    private final String provider;

    public ConfiguredPaymentCapturePolicy(
            Environment environment,
            @Value("${app.payments.provider:disabled}") String provider
    ) {
        this.environment = environment;
        this.provider = provider == null ? "disabled" : provider.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public void requireCaptureAvailable(PaymentMethod method) {
        if (method == PaymentMethod.CASH) {
            return;
        }
        boolean nonProductionTestMode = !environment.acceptsProfiles(Profiles.of("prod"))
                && "test".equals(provider);
        if (nonProductionTestMode) {
            return;
        }
        throw new AuthException("Only cash payments are available until a trusted live payment provider is connected",
                HttpStatus.CONFLICT);
    }
}
