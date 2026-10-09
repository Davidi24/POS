package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class OptionGroupNameAlreadyExistsException extends AuthException {

    public OptionGroupNameAlreadyExistsException() {
        super("Option group name already in use for this restaurant", HttpStatus.CONFLICT);
    }
}
