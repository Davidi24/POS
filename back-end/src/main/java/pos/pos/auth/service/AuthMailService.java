package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import pos.pos.config.properties.AppMailProperties;

import java.time.Duration;


// checked
// tested
@Service
@RequiredArgsConstructor
public class AuthMailService {

    private final JavaMailSender mailSender;
    private final AppMailProperties appMailProperties;

    public void sendPasswordResetEmail(
            String recipientEmail,
            String firstName,
            String subject,
            String resetUrl,
            Duration tokenTtl
    ) {
        String name = normalizeName(firstName);
        String body = """
                Hello %s,

                We received a request to reset your POS password.

                Reset your password using this link:
                %s

                This link expires in %d minutes.

                If you did not request this, you can ignore this email.
                """.formatted(name, resetUrl, tokenTtl.toMinutes());

        send(recipientEmail, subject, body);
    }

    public void sendAccountSetupEmail(
            String recipientEmail,
            String firstName,
            String setupUrl,
            Duration tokenTtl,
            String restaurantName
    ) {
        String name = normalizeName(firstName);
        String targetRestaurant = normalizeRestaurantName(restaurantName);
        String body = """
                Hello %s,

                Your POS owner account for %s is ready.

                Set your password using this link:
                %s

                This link expires in %d minutes.

                If you were not expecting this invitation, you can ignore this email.
                """.formatted(name, targetRestaurant, setupUrl, tokenTtl.toMinutes());

        send(recipientEmail, "Set up your POS owner account", body);
    }

    public void sendEmailVerificationEmail(
            String recipientEmail,
            String firstName,
            String subject,
            String verificationUrl,
            Duration tokenTtl
    ) {
        String name = normalizeName(firstName);
        String body = """
                Hello %s,

                Verify your email address for your POS account using this link:
                %s

                This link expires in %d hours.

                If you did not create this account, you can ignore this email.
                """.formatted(name, verificationUrl, Math.max(1, tokenTtl.toHours()));

        send(recipientEmail, subject, body);
    }

    public void sendPasswordChangedNotificationEmail(
            String recipientEmail,
            String firstName
    ) {
        String name = normalizeName(firstName);
        String body = """
                Hello %s,

                Your POS password was changed successfully.

                If you did not make this change, contact support immediately.
                """.formatted(name);

        send(recipientEmail, "Your POS password was changed", body);
    }

    private void send(String recipientEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(appMailProperties.getFrom());
        message.setTo(recipientEmail);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }

    private String normalizeName(String firstName) {
        if (firstName == null || firstName.isBlank()) {
            return "there";
        }

        return firstName.trim();
    }

    private String normalizeRestaurantName(String restaurantName) {
        if (restaurantName == null || restaurantName.isBlank()) {
            return "your restaurant";
        }

        return restaurantName.trim();
    }
}
