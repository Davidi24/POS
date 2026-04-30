package pos.pos.exception.settings;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class SettingsBusinessHourNotFoundException extends AuthException {

    public SettingsBusinessHourNotFoundException() {
        super("Business hour not found", HttpStatus.NOT_FOUND);
    }
}
