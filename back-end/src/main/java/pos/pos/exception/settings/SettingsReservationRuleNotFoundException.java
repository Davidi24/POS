package pos.pos.exception.settings;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class SettingsReservationRuleNotFoundException extends AuthException {

    public SettingsReservationRuleNotFoundException() {
        super("Reservation rule not found", HttpStatus.NOT_FOUND);
    }
}
