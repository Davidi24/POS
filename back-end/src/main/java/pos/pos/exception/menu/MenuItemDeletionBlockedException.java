package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class MenuItemDeletionBlockedException extends AuthException {

    public MenuItemDeletionBlockedException() {
        super("Menu item cannot be deleted while it still has variants or option groups", HttpStatus.CONFLICT);
    }
}
