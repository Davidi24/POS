package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class OptionItemNameAlreadyExistsException extends AuthException {

    public OptionItemNameAlreadyExistsException() {
        super("Option item name already in use for this option group", HttpStatus.CONFLICT);
    }
}
