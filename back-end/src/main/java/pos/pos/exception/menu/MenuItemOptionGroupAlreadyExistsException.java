package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class MenuItemOptionGroupAlreadyExistsException extends AuthException {

    public MenuItemOptionGroupAlreadyExistsException() {
        super("Option group already linked to this menu item", HttpStatus.CONFLICT);
    }
}
