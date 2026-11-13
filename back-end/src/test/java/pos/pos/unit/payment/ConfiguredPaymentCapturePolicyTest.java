package pos.pos.unit.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;
import pos.pos.payment.enums.PaymentMethod;
import pos.pos.payment.service.ConfiguredPaymentCapturePolicy;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Configured payment capture policy")
class ConfiguredPaymentCapturePolicyTest {

    @Test
    void productionAllowsCashButRejectsEveryUnverifiedNonCashMethodEvenIfTestModeIsConfigured() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        ConfiguredPaymentCapturePolicy policy = new ConfiguredPaymentCapturePolicy(environment, "test");

        assertThatCode(() -> policy.requireCaptureAvailable(PaymentMethod.CASH)).doesNotThrowAnyException();
        for (PaymentMethod method : List.of(PaymentMethod.CARD, PaymentMethod.CONTACTLESS,
                PaymentMethod.DIGITAL_WALLET, PaymentMethod.GIFT_CARD, PaymentMethod.HOUSE_ACCOUNT,
                PaymentMethod.LOYALTY, PaymentMethod.BANK_TRANSFER, PaymentMethod.OTHER)) {
            assertThatThrownBy(() -> policy.requireCaptureAvailable(method))
                    .isInstanceOf(AuthException.class)
                    .satisfies(error -> assertThat(((AuthException) error).getStatus()).isEqualTo(HttpStatus.CONFLICT));
        }
    }

    @Test
    void explicitTestProviderAllowsSimulationOnlyOutsideProduction() {
        ConfiguredPaymentCapturePolicy test = new ConfiguredPaymentCapturePolicy(new MockEnvironment(), "test");
        ConfiguredPaymentCapturePolicy disabled = new ConfiguredPaymentCapturePolicy(new MockEnvironment(), "disabled");

        assertThatCode(() -> test.requireCaptureAvailable(PaymentMethod.CARD)).doesNotThrowAnyException();
        assertThatThrownBy(() -> disabled.requireCaptureAvailable(PaymentMethod.CARD))
                .isInstanceOf(AuthException.class);
    }
}
