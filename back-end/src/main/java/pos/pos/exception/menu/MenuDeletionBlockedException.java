package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class MenuDeletionBlockedException extends AuthException {

    public MenuDeletionBlockedException() {
        super("Menu cannot be deleted while it still has sections", HttpStatus.CONFLICT);
    }
}
