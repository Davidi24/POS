package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class MenuVariantNameAlreadyExistsException extends AuthException {

    public MenuVariantNameAlreadyExistsException() {
        super("Menu variant name already in use for this item", HttpStatus.CONFLICT);
    }
}
