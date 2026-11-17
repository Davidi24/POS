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

    public enum Reason { WRONG_CREDENTIALS, ACCOUNT_LOCKED, ACCOUNT_OFF, EMAIL_NOT_CONFIRMED, RESTAURANT_CLOSED, TOO_MANY_TRIES, DEVICE_PAUSED, NETWORK_PAUSED }

    private final Reason reason;
    private final OffsetDateTime lockedUntil;

    public LoginRefusedException(Reason reason, String message, HttpStatus status, OffsetDateTime lockedUntil) {
        super(message, status);
        this.reason = reason;
        this.lockedUntil = lockedUntil;
    }
}
