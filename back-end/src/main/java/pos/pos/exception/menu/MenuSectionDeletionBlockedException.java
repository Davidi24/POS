package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class MenuSectionDeletionBlockedException extends AuthException {

    public MenuSectionDeletionBlockedException() {
        super("Menu section cannot be deleted while it still has items", HttpStatus.CONFLICT);
    }
}
