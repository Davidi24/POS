package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class MenuItemNotFoundException extends AuthException {

    public MenuItemNotFoundException() {
        super("Menu item not found", HttpStatus.NOT_FOUND);
    }
}
