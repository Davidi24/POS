package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class MenuItemOptionGroupItemMismatchException extends AuthException {

    public MenuItemOptionGroupItemMismatchException() {
        super("Menu item option group link does not belong to this item", HttpStatus.CONFLICT);
    }
}
