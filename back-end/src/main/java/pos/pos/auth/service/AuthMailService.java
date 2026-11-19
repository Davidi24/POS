package pos.pos.auth.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import pos.pos.config.properties.AppMailProperties;
import pos.pos.i18n.Words;
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
 * mail server is down. Without an outbox (plain unit tests) they go straight to the mail server. Each is written in the
 * person's own language from Profile (user, 2026-11-18; {@link Words}), English when they haven't chosen one.
 */
@Service
public class AuthMailService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AuthMailService.class);
    private static final String OWNER_INVITE_SUBJECT = Words.en("Set up your POS owner account");

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
        deliver(EmailKind.PASSWORD_RESET, recipientEmail, subject, resetBody("en", firstName, resetUrl, tokenTtl), null, null, null);
    }

    /** The reset link for someone's account; it isn't sent after the link expires. */
    public void sendPasswordResetEmail(User user, String subject, String resetUrl, Duration tokenTtl, OffsetDateTime expiresAt) {
        String lang = user.getLanguage();
        deliver(EmailKind.PASSWORD_RESET, user.getEmail(), subject(lang, subject, Words.en("Reset your POS password")),
                resetBody(lang, user.getFirstName(), resetUrl, tokenTtl), user.getId(), user.getRestaurantId(), expiresAt);
    }

    public void sendAccountSetupEmail(
            String recipientEmail,
            String firstName,
            String setupUrl,
            Duration tokenTtl,
            String restaurantName
    ) {
        String body = setupBody("en", firstName, null, recipientEmail, setupUrl, tokenTtl, restaurantName);
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
        String lang = owner.getLanguage();
        String body = setupBody(lang, owner.getFirstName(), owner.getUsername(), owner.getEmail(), setupUrl, tokenTtl, restaurantName);
        if (outbox == null) {
            sendAccountSetupEmail(owner.getEmail(), owner.getFirstName(), setupUrl, tokenTtl, restaurantName);
            return;
        }
        deliver(EmailKind.OWNER_INVITE, owner.getEmail(), Words.t(lang, OWNER_INVITE_SUBJECT), body, owner.getId(), owner.getRestaurantId(), expiresAt);
    }

    public void sendEmailVerificationEmail(
            String recipientEmail,
            String firstName,
            String subject,
            String verificationUrl,
            Duration tokenTtl
    ) {
        sendEmailVerificationEmail("en", recipientEmail, firstName, subject, verificationUrl, tokenTtl);
    }

    /** The same in the person's [lang]. */
    public void sendEmailVerificationEmail(
            String lang,
            String recipientEmail,
            String firstName,
            String subject,
            String verificationUrl,
            Duration tokenTtl
    ) {
        String body = Words.t(lang, """
                Hello {},

                Verify your email address for your POS account using this link:
                {}

                This link expires in {}.

                If you did not create this account, you can ignore this email.
                """, name(lang, firstName), verificationUrl, inWords(lang, tokenTtl));

        deliver(EmailKind.EMAIL_VERIFICATION, recipientEmail, subject(lang, subject, Words.en("Verify your POS email")), body, null, null, null);
    }

    public void sendPasswordChangedNotificationEmail(
            String recipientEmail,
            String firstName
    ) {
        sendPasswordChangedNotificationEmail("en", recipientEmail, firstName);
    }

    /** The same in the person's [lang]. */
    public void sendPasswordChangedNotificationEmail(String lang, String recipientEmail, String firstName) {
        String body = Words.t(lang, """
                Hello {},

                Your POS password was changed successfully.

                If you did not make this change, contact support immediately.
                """, name(lang, firstName));

        deliver(EmailKind.PASSWORD_CHANGED, recipientEmail, Words.t(lang, "Your POS password was changed"), body, null, null, null);
    }

    /** Profile → change email: the link that makes [newEmail] the account's email (user, 2026-11-18). */
    public void sendEmailChangeConfirmation(User user, String newEmail, String url, Duration ttl) {
        String lang = user.getLanguage();
        String body = Words.t(lang, """
                Hello {},

                You asked to use this email for your POS account. Confirm it here:
                {}

                This link works once and expires in {}. Until then your old email stays.

                If you didn't ask for this, ignore this email: nothing changes.
                """, name(lang, user.getFirstName()), url, inWords(lang, ttl));
        deliver(EmailKind.EMAIL_VERIFICATION, newEmail, Words.t(lang, "Confirm your new email"), body, user.getId(), user.getRestaurantId(), null);
    }

    /** After a person changed their own username, so they notice if it wasn't them. */
    public void sendUsernameChangedNotice(User user, String oldUsername) {
        String lang = user.getLanguage();
        String body = Words.t(lang, """
                Hello {},

                Your POS username was changed from "{}" to "{}". Sign in with the new one from now on.

                If this wasn't you, change your password and tell your manager.
                """, name(lang, user.getFirstName()), oldUsername, user.getUsername());
        deliver(EmailKind.USERNAME_CHANGED, user.getEmail(), Words.t(lang, "Your POS username was changed"), body, user.getId(), user.getRestaurantId(), null);
    }

    /** To the account's current email when someone asks to change it, so the owner of the address knows. */
    public void sendEmailChangeNotice(User user, String maskedNewEmail) {
        String lang = user.getLanguage();
        String body = Words.t(lang, """
                Hello {},

                Someone asked to change the email of your POS account to {}. It changes only when the link sent there is used.

                If this wasn't you, change your password and tell your manager.
                """, name(lang, user.getFirstName()), maskedNewEmail);
        deliver(EmailKind.EMAIL_CHANGE, user.getEmail(), Words.t(lang, "Your POS email is being changed"), body, user.getId(), user.getRestaurantId(), null);
    }

    /** "Forgot password?" reached today's limit, so no more links come today (user, 2026-11-17). */
    public void sendResetLinkLimitEmail(User user, int limit) {
        String lang = user.getLanguage();
        String body = Words.t(lang, """
                Hello {},

                We were asked {} times today to send you a link to choose a new POS password. That's the most we send in a day, so no more links will come today.

                If you asked for them, use the newest link we sent. If it has expired, try again tomorrow.

                If you didn't ask for them, you can ignore this email: your password hasn't changed.
                """, name(lang, user.getFirstName()), limit);
        deliver(EmailKind.PASSWORD_LIMIT, user.getEmail(), Words.t(lang, "No more password links today"), body, user.getId(), user.getRestaurantId(), null);
    }

    /** The password was changed as many times today as allowed, which may mean someone else is in the account (user, 2026-11-17). */
    public void sendPasswordChangeLimitEmail(User user, int limit) {
        String lang = user.getLanguage();
        String body = Words.t(lang, """
                Hello {},

                Your POS password was changed {} times today. That's the most allowed in a day, so it can't be changed again until tomorrow.

                If you didn't make these changes, someone may be using your account. Use "Forgot password?" in the POS app to choose a new password, and tell your manager.
                """, name(lang, user.getFirstName()), limit);
        deliver(EmailKind.PASSWORD_LIMIT, user.getEmail(), Words.t(lang, "Your POS password was changed many times today"), body, user.getId(),
                user.getRestaurantId(), null);
    }

    private String resetBody(String lang, String firstName, String resetUrl, Duration tokenTtl) {
        return Words.t(lang, """
                Hello {},

                We received a request to reset your POS password.

                Choose a new password using this link:
                {}

                This link works once and expires in {}.

                If you did not request this, you can ignore this email.
                """, name(lang, firstName), resetUrl, inWords(lang, tokenTtl));
    }

    private String setupBody(String lang, String firstName, String username, String email, String setupUrl, Duration tokenTtl, String restaurantName) {
        String signIn = username == null || username.isBlank()
                ? Words.t(lang, "Then sign in to the POS app with this email address ({}).", email)
                : Words.t(lang, "Then sign in to the POS app with your username \"{}\" or this email address.", username.trim());
        return Words.t(lang, """
                Hello {},

                You're the owner of {} on POS, and your account is ready.

                Choose your password here:
                {}

                {}

                This link works once and expires in {}. If it expires, ask for a new invitation.

                If you were not expecting this invitation, you can ignore this email.
                """, name(lang, firstName), restaurantName(lang, restaurantName), setupUrl, signIn, inWords(lang, tokenTtl));
    }

    /** The configured subject when it was changed from the usual one; otherwise the usual one in [lang]. */
    private static String subject(String lang, String configured, String usual) {
        return configured == null || configured.isBlank() || configured.equals(usual) ? Words.t(lang, usual) : configured;
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
        return inWords("en", duration);
    }

    /** The same in [lang]. */
    public static String inWords(String lang, Duration duration) {
        long minutes = Math.max(1, duration.toMinutes());
        if (minutes < 120) return minutes == 1 ? Words.t(lang, "1 minute") : Words.t(lang, "{} minutes", minutes);
        long hours = duration.toHours();
        if (hours < 48) return Words.t(lang, "{} hours", hours);
        return Words.t(lang, "{} days", duration.toDays());
    }

    private static String name(String lang, String firstName) {
        return firstName == null || firstName.isBlank() ? Words.t(lang, "there") : firstName.trim();
    }

    private static String restaurantName(String lang, String restaurantName) {
        return restaurantName == null || restaurantName.isBlank() ? Words.t(lang, "your restaurant") : restaurantName.trim();
    }
}
