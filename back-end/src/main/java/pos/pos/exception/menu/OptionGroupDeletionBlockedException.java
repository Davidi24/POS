package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class OptionGroupDeletionBlockedException extends AuthException {

    public OptionGroupDeletionBlockedException() {
        super("Option group cannot be deleted while it still has items or menu item assignments", HttpStatus.CONFLICT);
    }
}
