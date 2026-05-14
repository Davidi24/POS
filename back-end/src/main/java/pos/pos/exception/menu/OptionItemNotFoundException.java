package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class OptionItemNotFoundException extends AuthException {

    public OptionItemNotFoundException() {
        super("Option item not found", HttpStatus.NOT_FOUND);
    }
}
