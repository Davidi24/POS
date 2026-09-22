package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class MenuSectionDeletionRequiresConfirmationException extends AuthException {

    public MenuSectionDeletionRequiresConfirmationException() {
        super(
                "This is the \"Uncategorized\" section -- there is nowhere else to move its items. "
                        + "Delete its items to remove it, or move its items elsewhere first.",
                HttpStatus.CONFLICT
        );
    }
}
