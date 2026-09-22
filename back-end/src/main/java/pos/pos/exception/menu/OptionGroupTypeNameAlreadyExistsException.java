package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class OptionGroupTypeNameAlreadyExistsException extends AuthException {

    public OptionGroupTypeNameAlreadyExistsException() {
        super("Option group type name already in use", HttpStatus.CONFLICT);
    }
}
