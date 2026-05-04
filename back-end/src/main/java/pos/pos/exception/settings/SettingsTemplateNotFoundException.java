package pos.pos.exception.settings;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class SettingsTemplateNotFoundException extends AuthException {

    public SettingsTemplateNotFoundException() {
        super("Settings template not found", HttpStatus.NOT_FOUND);
    }
}
