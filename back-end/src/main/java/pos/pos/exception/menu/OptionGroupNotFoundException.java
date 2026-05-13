package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class OptionGroupNotFoundException extends AuthException {

    public OptionGroupNotFoundException() {
        super("Option group not found", HttpStatus.NOT_FOUND);
    }
}
