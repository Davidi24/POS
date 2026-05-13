package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class MenuCodeAlreadyExistsException extends AuthException {

    public MenuCodeAlreadyExistsException() {
        super("Menu code already in use for this restaurant", HttpStatus.CONFLICT);
    }
}
