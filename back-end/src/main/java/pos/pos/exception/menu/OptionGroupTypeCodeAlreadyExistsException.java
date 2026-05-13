package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class OptionGroupTypeCodeAlreadyExistsException extends AuthException {

    public OptionGroupTypeCodeAlreadyExistsException() {
        super("Option group type code already in use", HttpStatus.CONFLICT);
    }
}
