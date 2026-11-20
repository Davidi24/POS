package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class OptionItemGroupMismatchException extends AuthException {

    public OptionItemGroupMismatchException() {
        super("Option item does not belong to this option group", HttpStatus.CONFLICT);
    }
}
