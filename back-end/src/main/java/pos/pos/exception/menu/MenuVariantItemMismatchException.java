package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class MenuVariantItemMismatchException extends AuthException {

    public MenuVariantItemMismatchException() {
        super("Menu variant does not belong to this item", HttpStatus.CONFLICT);
    }
}
