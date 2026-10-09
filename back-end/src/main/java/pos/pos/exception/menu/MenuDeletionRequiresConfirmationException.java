package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class MenuDeletionRequiresConfirmationException extends AuthException {

    public MenuDeletionRequiresConfirmationException() {
        super(
                "This is the \"Uncategorized\" menu -- there is nowhere else to move its sections. "
                        + "Delete its items to remove it, or move its sections elsewhere first.",
                HttpStatus.CONFLICT
        );
    }
}
