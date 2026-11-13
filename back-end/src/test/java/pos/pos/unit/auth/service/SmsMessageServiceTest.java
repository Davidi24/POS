package pos.pos.unit.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import pos.pos.auth.enums.SmsDeliveryMode;
import pos.pos.auth.service.SmsMessageService;
import pos.pos.config.properties.SmsAuthProperties;
import pos.pos.exception.auth.AuthException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
@DisplayName("SmsMessageService")
class SmsMessageServiceTest {

    @Nested
    @DisplayName("isEnabled")
    class IsEnabledTests {

        @Test
        @DisplayName("Should return true when delivery mode is log only")
        void shouldReturnTrueWhenDeliveryModeIsLogOnly() {
            SmsMessageService service = new SmsMessageService(properties(SmsDeliveryMode.LOG_ONLY));

            assertThat(service.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Should return false when delivery mode is disabled")
        void shouldReturnFalseWhenDeliveryModeIsDisabled() {
            SmsMessageService service = new SmsMessageService(properties(SmsDeliveryMode.DISABLED));

            assertThat(service.isEnabled()).isFalse();
        }
    }

    @Nested
    @DisplayName("sendPasswordResetCode")
    class SendPasswordResetCodeTests {

        @Test
        @DisplayName("Should throw when SMS delivery is disabled")
        void shouldThrowWhenSmsDeliveryIsDisabled() {
            SmsMessageService service = new SmsMessageService(properties(SmsDeliveryMode.DISABLED));

            assertThatThrownBy(() -> service.sendPasswordResetCode("+49-555-0101", "Mila", "123456", Duration.ofMinutes(10)))
                    .isInstanceOf(AuthException.class)
                    .hasMessage("SMS delivery is currently unavailable");
        }

        @Test
        @DisplayName("Should not log the reset code, recipient or message body")
        void shouldNotLogResetCodeRecipientOrBody(CapturedOutput output) {
            SmsMessageService service = new SmsMessageService(properties(SmsDeliveryMode.LOG_ONLY));

            service.sendPasswordResetCode("+49-555-0101", " Mila ", "123456", Duration.ofMinutes(10));

            assertThat(output.getOut())
                    .contains("SMS delivery [LOG_ONLY] requested; recipient and body omitted from logs")
                    .doesNotContain("+49-555-0101", "Mila", "123456", "password reset code", "expires in 10 minutes");
        }
    }

    @Nested
    @DisplayName("sendPhoneVerificationCode")
    class SendPhoneVerificationCodeTests {

        @Test
        @DisplayName("Should not log the verification code, recipient or message body")
        void shouldNotLogVerificationCodeRecipientOrBody(CapturedOutput output) {
            SmsMessageService service = new SmsMessageService(properties(SmsDeliveryMode.LOG_ONLY));

            service.sendPhoneVerificationCode("+49-555-0202", "Nora", "654321", Duration.ofMinutes(5));

            assertThat(output.getOut())
                    .contains("SMS delivery [LOG_ONLY] requested; recipient and body omitted from logs")
                    .doesNotContain("+49-555-0202", "Nora", "654321", "phone verification code", "expires in 5 minutes");
        }

        @Test
        @DisplayName("Should fall back to generic greeting and minimum one minute")
        void shouldFallBackToGenericGreetingAndMinimumOneMinute(CapturedOutput output) {
            SmsMessageService service = new SmsMessageService(properties(SmsDeliveryMode.LOG_ONLY));

            service.sendPhoneVerificationCode("+49-555-0303", "   ", "111222", Duration.ofSeconds(30));

            assertThat(output.getOut())
                    .contains("SMS delivery [LOG_ONLY] requested; recipient and body omitted from logs")
                    .doesNotContain("+49-555-0303", "111222", "Hello there", "expires in 1 minutes");
        }
    }

    private SmsAuthProperties properties(SmsDeliveryMode deliveryMode) {
        SmsAuthProperties properties = new SmsAuthProperties();
        properties.setDeliveryMode(deliveryMode);
        return properties;
    }
}
