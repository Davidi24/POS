package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class MenuItemSectionMismatchException extends AuthException {

    public MenuItemSectionMismatchException() {
        super("Menu item does not belong to this section", HttpStatus.CONFLICT);
    }
}
