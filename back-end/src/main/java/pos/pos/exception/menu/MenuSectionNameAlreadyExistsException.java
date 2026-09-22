package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class MenuSectionNameAlreadyExistsException extends AuthException {

    public MenuSectionNameAlreadyExistsException() {
        super("Menu section name already in use for this menu", HttpStatus.CONFLICT);
    }
}
