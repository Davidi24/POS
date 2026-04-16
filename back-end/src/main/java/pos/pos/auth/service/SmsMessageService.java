package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pos.pos.config.properties.SmsAuthProperties;
import pos.pos.exception.auth.AuthException;

import java.time.Duration;


// checked
// tested
@Service
@RequiredArgsConstructor
public class SmsMessageService {

    private static final Logger logger = LoggerFactory.getLogger(SmsMessageService.class);

    private final SmsAuthProperties smsAuthProperties;

    public boolean isEnabled() {
        return smsAuthProperties.isEnabled();
    }

    // used to change the password
    public void sendPasswordResetCode(String phoneNumber, String firstName, String code, Duration ttl) {
        send(phoneNumber, """
                Hello %s,

                Your POS password reset code is: %s

                This code expires in %d minutes.

                If you did not request this, you can ignore this message.
                """.formatted(normalizeName(firstName), code, Math.max(1, ttl.toMinutes())));
    }

    // Used to verify number exist
    public void sendPhoneVerificationCode(String phoneNumber, String firstName, String code, Duration ttl) {
        send(phoneNumber, """
                Hello %s,

                Your POS phone verification code is: %s

                This code expires in %d minutes.
                """.formatted(normalizeName(firstName), code, Math.max(1, ttl.toMinutes())));
    }

    private void send(String phoneNumber, String body) {
        if (!isEnabled()) {
            throw new AuthException("SMS delivery is currently unavailable", HttpStatus.SERVICE_UNAVAILABLE);
        }

        // FUTURE PROVIDER: replace LOG_ONLY delivery with a real SMS gateway such as Twilio, MessageBird, or AWS SNS.
        logger.info("SMS delivery [{}] -> {}\n{}", smsAuthProperties.getDeliveryMode(), phoneNumber, body);
    }

    private String normalizeName(String firstName) {
        if (firstName == null || firstName.isBlank()) {
            return "there";
        }

        return firstName.trim();
    }
}
