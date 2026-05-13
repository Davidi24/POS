package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class MenuItemOptionGroupRestaurantMismatchException extends AuthException {

    public MenuItemOptionGroupRestaurantMismatchException() {
        super("Option group does not belong to the same restaurant as this menu item", HttpStatus.CONFLICT);
    }
}
