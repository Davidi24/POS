package pos.pos.unit.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import pos.pos.auth.service.AuthMailService;
import pos.pos.config.properties.AppMailProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthMailService")
class AuthMailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Nested
    @DisplayName("sendPasswordResetEmail")
    class SendPasswordResetEmailTests {

        @Test
        @DisplayName("Should send password reset email with expected payload")
        void shouldSendPasswordResetEmailWithExpectedPayload() {
            AuthMailService authMailService = service("no-reply@pos.local");

            authMailService.sendPasswordResetEmail(
                    "cashier@pos.local",
                    "  Casey  ",
                    "Reset your POS password",
                    "https://pos.local/reset?token=abc",
                    Duration.ofMinutes(30)
            );

            SimpleMailMessage message = capturedMessage();

            assertThat(message.getFrom()).isEqualTo("no-reply@pos.local");
            assertThat(message.getTo()).containsExactly("cashier@pos.local");
            assertThat(message.getSubject()).isEqualTo("Reset your POS password");
            assertThat(message.getText())
                    .contains("Hello Casey,")
                    .contains("Reset your password using this link:")
                    .contains("https://pos.local/reset?token=abc")
                    .contains("This link expires in 30 minutes.")
                    .contains("If you did not request this, you can ignore this email.");
        }

        @Test
        @DisplayName("Should fall back to generic greeting when first name is blank")
        void shouldFallBackToGenericGreetingWhenFirstNameBlank() {
            AuthMailService authMailService = service("no-reply@pos.local");

            authMailService.sendPasswordResetEmail(
                    "cashier@pos.local",
                    "   ",
                    "Reset your POS password",
                    "https://pos.local/reset?token=abc",
                    Duration.ofMinutes(10)
            );

            assertThat(capturedMessage().getText()).contains("Hello there,");
        }
    }

    @Nested
    @DisplayName("sendEmailVerificationEmail")
    class SendEmailVerificationEmailTests {

        @Test
        @DisplayName("Should send verification email with minimum one hour display")
        void shouldSendVerificationEmailWithMinimumOneHourDisplay() {
            AuthMailService authMailService = service("no-reply@pos.local");

            authMailService.sendEmailVerificationEmail(
                    "owner@pos.local",
                    "Olivia",
                    "Verify your POS email",
                    "https://pos.local/verify?token=xyz",
                    Duration.ofMinutes(30)
            );

            SimpleMailMessage message = capturedMessage();

            assertThat(message.getSubject()).isEqualTo("Verify your POS email");
            assertThat(message.getText())
                    .contains("Hello Olivia,")
                    .contains("Verify your email address for your POS account using this link:")
                    .contains("https://pos.local/verify?token=xyz")
                    .contains("This link expires in 1 hours.")
                    .contains("If you did not create this account, you can ignore this email.");
        }

        @Test
        @DisplayName("Should display full hour count when token TTL is multiple hours")
        void shouldDisplayFullHourCountWhenTokenTtlIsMultipleHours() {
            AuthMailService authMailService = service("no-reply@pos.local");

            authMailService.sendEmailVerificationEmail(
                    "manager@pos.local",
                    "Maria",
                    "Verify your POS email",
                    "https://pos.local/verify?token=xyz",
                    Duration.ofHours(24)
            );

            assertThat(capturedMessage().getText()).contains("This link expires in 24 hours.");
        }
    }

    @Nested
    @DisplayName("sendPasswordChangedNotificationEmail")
    class SendPasswordChangedNotificationEmailTests {

        @Test
        @DisplayName("Should send password changed notification with fixed subject")
        void shouldSendPasswordChangedNotificationWithFixedSubject() {
            AuthMailService authMailService = service("no-reply@pos.local");

            authMailService.sendPasswordChangedNotificationEmail("admin@pos.local", "Amina");

            SimpleMailMessage message = capturedMessage();

            assertThat(message.getSubject()).isEqualTo("Your POS password was changed");
            assertThat(message.getText())
                    .contains("Hello Amina,")
                    .contains("Your POS password was changed successfully.")
                    .contains("If you did not make this change, contact support immediately.");
        }
    }

    private AuthMailService service(String fromAddress) {
        AppMailProperties properties = new AppMailProperties();
        properties.setFrom(fromAddress);
        return new AuthMailService(mailSender, properties);
    }

    private SimpleMailMessage capturedMessage() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }
}
