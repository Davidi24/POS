package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class OptionGroupTypeNotFoundException extends AuthException {

    public OptionGroupTypeNotFoundException() {
        super("Option group type not found", HttpStatus.NOT_FOUND);
    }
}
