package pos.pos.auth.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import pos.pos.config.properties.AppMailProperties;
import pos.pos.mail.EmailKind;
import pos.pos.mail.EmailOutboxService;
import pos.pos.user.entity.User;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;


// checked
// tested
/**
 * Account emails. They go through the email outbox, so they're sent after the change commits and retried while the
 * mail server is down. Without an outbox (plain unit tests) they go straight to the mail server.
 */
@Service
public class AuthMailService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthMailService.class);
    private static final String OWNER_INVITE_SUBJECT = "Set up your POS owner account";

    private final JavaMailSender mailSender;
    private final AppMailProperties appMailProperties;
    private final EmailOutboxService outbox;

    public AuthMailService(JavaMailSender mailSender, AppMailProperties appMailProperties) {
        this(mailSender, appMailProperties, null);
    }

    @Autowired
    public AuthMailService(JavaMailSender mailSender, AppMailProperties appMailProperties, EmailOutboxService outbox) {
        this.mailSender = mailSender;
        this.appMailProperties = appMailProperties;
        this.outbox = outbox;
    }

    public void sendPasswordResetEmail(
            String recipientEmail,
            String firstName,
            String subject,
            String resetUrl,
            Duration tokenTtl
    ) {
        deliver(EmailKind.PASSWORD_RESET, recipientEmail, subject, resetBody(firstName, resetUrl, tokenTtl), null, null, null);
    }

    /** The reset link for someone's account; it isn't sent after the link expires. */
    public void sendPasswordResetEmail(User user, String subject, String resetUrl, Duration tokenTtl, OffsetDateTime expiresAt) {
        deliver(EmailKind.PASSWORD_RESET, user.getEmail(), subject, resetBody(user.getFirstName(), resetUrl, tokenTtl),
                user.getId(), user.getRestaurantId(), expiresAt);
    }

    public void sendAccountSetupEmail(
            String recipientEmail,
            String firstName,
            String setupUrl,
            Duration tokenTtl,
            String restaurantName
    ) {
        String body = setupBody(firstName, null, recipientEmail, setupUrl, tokenTtl, restaurantName);
        if (outbox == null) {
            // Straight to the mail server: a failed invitation must not look sent.
            SimpleMailMessage message = message(recipientEmail, OWNER_INVITE_SUBJECT, body);
            try {
                mailSender.send(message);
            } catch (org.springframework.mail.MailException failure) {
                throw new pos.pos.exception.auth.AuthException("The invitation could not be sent. Try again.", org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
            }
            return;
        }
        deliver(EmailKind.OWNER_INVITE, recipientEmail, OWNER_INVITE_SUBJECT, body, null, null, null);
    }

    /** The owner's invitation to set a password; it isn't sent after the link expires. */
    public void sendAccountSetupEmail(User owner, String setupUrl, Duration tokenTtl, OffsetDateTime expiresAt, String restaurantName) {
        String body = setupBody(owner.getFirstName(), owner.getUsername(), owner.getEmail(), setupUrl, tokenTtl, restaurantName);
        if (outbox == null) {
            sendAccountSetupEmail(owner.getEmail(), owner.getFirstName(), setupUrl, tokenTtl, restaurantName);
            return;
        }
        deliver(EmailKind.OWNER_INVITE, owner.getEmail(), OWNER_INVITE_SUBJECT, body, owner.getId(), owner.getRestaurantId(), expiresAt);
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

                This link expires in %s.

                If you did not create this account, you can ignore this email.
                """.formatted(name, verificationUrl, inWords(tokenTtl));

        deliver(EmailKind.EMAIL_VERIFICATION, recipientEmail, subject, body, null, null, null);
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

        deliver(EmailKind.PASSWORD_CHANGED, recipientEmail, "Your POS password was changed", body, null, null, null);
    }

    /** Profile → change email: the link that makes [newEmail] the account's email (user, 2026-11-18). */
    public void sendEmailChangeConfirmation(User user, String newEmail, String url, Duration ttl) {
        String body = """
                Hello %s,

                You asked to use this email for your POS account. Confirm it here:
                %s

                This link works once and expires in %s. Until then your old email stays.

                If you didn't ask for this, ignore this email: nothing changes.
                """.formatted(normalizeName(user.getFirstName()), url, inWords(ttl));
        deliver(EmailKind.EMAIL_VERIFICATION, newEmail, "Confirm your new email", body, user.getId(), user.getRestaurantId(), null);
    }

    /** After a person changed their own username, so they notice if it wasn't them. */
    public void sendUsernameChangedNotice(User user, String oldUsername) {
        String body = """
                Hello %s,

                Your POS username was changed from "%s" to "%s". Sign in with the new one from now on.

                If this wasn't you, change your password and tell your manager.
                """.formatted(normalizeName(user.getFirstName()), oldUsername, user.getUsername());
        deliver(EmailKind.USERNAME_CHANGED, user.getEmail(), "Your POS username was changed", body, user.getId(), user.getRestaurantId(), null);
    }

    /** To the account's current email when someone asks to change it, so the owner of the address knows. */
    public void sendEmailChangeNotice(User user, String maskedNewEmail) {
        String body = """
                Hello %s,

                Someone asked to change the email of your POS account to %s. It changes only when the link sent there is used.

                If this wasn't you, change your password and tell your manager.
                """.formatted(normalizeName(user.getFirstName()), maskedNewEmail);
        deliver(EmailKind.EMAIL_CHANGE, user.getEmail(), "Your POS email is being changed", body, user.getId(), user.getRestaurantId(), null);
    }

    /** "Forgot password?" reached today's limit, so no more links come today (user, 2026-11-17). */
    public void sendResetLinkLimitEmail(User user, int limit) {
        String body = """
                Hello %s,

                We were asked %d times today to send you a link to choose a new POS password. That's the most we send in a day, so no more links will come today.

                If you asked for them, use the newest link we sent. If it has expired, try again tomorrow.

                If you didn't ask for them, you can ignore this email: your password hasn't changed.
                """.formatted(normalizeName(user.getFirstName()), limit);
        deliver(EmailKind.PASSWORD_LIMIT, user.getEmail(), "No more password links today", body, user.getId(), user.getRestaurantId(), null);
    }

    /** The password was changed as many times today as allowed, which may mean someone else is in the account (user, 2026-11-17). */
    public void sendPasswordChangeLimitEmail(User user, int limit) {
        String body = """
                Hello %s,

                Your POS password was changed %d times today. That's the most allowed in a day, so it can't be changed again until tomorrow.

                If you didn't make these changes, someone may be using your account. Use "Forgot password?" in the POS app to choose a new password, and tell your manager.
                """.formatted(normalizeName(user.getFirstName()), limit);
        deliver(EmailKind.PASSWORD_LIMIT, user.getEmail(), "Your POS password was changed many times today", body, user.getId(),
                user.getRestaurantId(), null);
    }

    private String resetBody(String firstName, String resetUrl, Duration tokenTtl) {
        return """
                Hello %s,

                We received a request to reset your POS password.

                Choose a new password using this link:
                %s

                This link works once and expires in %s.

                If you did not request this, you can ignore this email.
                """.formatted(normalizeName(firstName), resetUrl, inWords(tokenTtl));
    }

    private String setupBody(String firstName, String username, String email, String setupUrl, Duration tokenTtl, String restaurantName) {
        String signIn = username == null || username.isBlank()
                ? "Then sign in to the POS app with this email address (%s).".formatted(email)
                : "Then sign in to the POS app with your username \"%s\" or this email address.".formatted(username.trim());
        return """
                Hello %s,

                You're the owner of %s on POS, and your account is ready.

                Choose your password here:
                %s

                %s

                This link works once and expires in %s. If it expires, ask for a new invitation.

                If you were not expecting this invitation, you can ignore this email.
                """.formatted(normalizeName(firstName), normalizeRestaurantName(restaurantName), setupUrl, signIn, inWords(tokenTtl));
    }

    private void deliver(EmailKind kind, String recipientEmail, String subject, String body, UUID userId, UUID restaurantId,
                         OffsetDateTime sendBefore) {
        if (outbox != null) {
            outbox.enqueue(new EmailOutboxService.OutgoingEmail(kind, recipientEmail, subject, body, userId, restaurantId, sendBefore));
            return;
        }
        // A mail server that is down must not undo what the person just did (adding staff, resetting a password).
        // The email can be sent again from the app; the failure is logged for whoever runs the server.
        try {
            mailSender.send(message(recipientEmail, subject, body));
        } catch (org.springframework.mail.MailException ex) {
            log.warn("Could not send the email \"{}\": {}", subject, ex.getMessage());
        }
    }

    private SimpleMailMessage message(String recipientEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(appMailProperties.getFrom());
        message.setTo(recipientEmail);
        message.setSubject(subject);
        message.setText(body);
        return message;
    }

    // "30 minutes", "24 hours", "3 days".
    static String inWords(Duration duration) {
        long minutes = Math.max(1, duration.toMinutes());
        if (minutes < 120) return minutes + (minutes == 1 ? " minute" : " minutes");
        long hours = duration.toHours();
        if (hours < 48) return hours + " hours";
        return duration.toDays() + " days";
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
