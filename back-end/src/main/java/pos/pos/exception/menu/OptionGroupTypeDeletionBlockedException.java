package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class OptionGroupTypeDeletionBlockedException extends AuthException {

    public OptionGroupTypeDeletionBlockedException() {
        super("Option group type cannot be deleted while it is still used by option groups", HttpStatus.CONFLICT);
    }
}
