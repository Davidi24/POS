package pos.pos.exception.auth;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;

/**
 * Sign-in refused, with why (user, 2026-11-16: "tell exactly what's wrong", the safe way): a wrong username or password is
 * always the same answer; the account being off, its email not confirmed or its restaurant closed are only told after the
 * right password; a lock, a paused device and a paused network say until when ([lockedUntil]).
 */
@Getter
public class LoginRefusedException extends AuthException {

    public enum Reason { WRONG_CREDENTIALS, ACCOUNT_LOCKED, ACCOUNT_OFF, EMAIL_NOT_CONFIRMED, RESTAURANT_CLOSED, TOO_MANY_TRIES, DEVICE_PAUSED, NETWORK_PAUSED,
        // The right password, but the person is signed in on as many devices as allowed: [devices] lists them to pick one to sign out.
        TOO_MANY_DEVICES,
        // A refresh for a sign-in that has ended: [signedOutReason] says why (password changed, signed out everywhere…).
        SIGNED_OUT,
        // PIN sign-in: a PIN nobody in the restaurant has; the till paused after 5 wrong PINs; the device isn't connected;
        // the till is outside its restaurant's network while its branch keeps tills on it (user, 2026-11-24).
        WRONG_PIN, PIN_PAUSED, DEVICE_NOT_CONNECTED, OUTSIDE_NETWORK,
        // The person may not sign in on this till or tablet (Devices → Who can sign in here, 2026-11-25).
        NOT_ON_THIS_DEVICE }

    private final Reason reason;
    private final OffsetDateTime lockedUntil;
    private final String signedOutReason;
    private final java.util.List<SignedInDevice> devices;

    public LoginRefusedException(Reason reason, String message, HttpStatus status, OffsetDateTime lockedUntil) {
        this(reason, message, status, lockedUntil, null, null);
    }

    public LoginRefusedException(Reason reason, String message, HttpStatus status, OffsetDateTime lockedUntil,
                                 String signedOutReason, java.util.List<SignedInDevice> devices) {
        super(message, status);
        this.reason = reason;
        this.lockedUntil = lockedUntil;
        this.signedOutReason = signedOutReason;
        this.devices = devices;
    }

    /** One of the person's signed-in devices, for choosing which to sign out. */
    /** [system] what it runs, [place] where its address is ("Tirana, Albania"), [sameNetwork] on the network of this sign-in. */
    public record SignedInDevice(java.util.UUID id, String name, OffsetDateTime lastUsedAt, OffsetDateTime signedInAt,
                                 String system, String place, boolean sameNetwork) {
        public SignedInDevice(java.util.UUID id, String name, OffsetDateTime lastUsedAt, OffsetDateTime signedInAt) {
            this(id, name, lastUsedAt, signedInAt, null, null, false);
        }
    }
}
