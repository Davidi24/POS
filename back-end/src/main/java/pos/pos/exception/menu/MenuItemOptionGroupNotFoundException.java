package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class MenuItemOptionGroupNotFoundException extends AuthException {

    public MenuItemOptionGroupNotFoundException() {
        super("Menu item option group link not found", HttpStatus.NOT_FOUND);
    }
}
