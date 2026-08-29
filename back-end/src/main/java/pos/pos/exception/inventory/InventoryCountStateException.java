package pos.pos.exception.inventory;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class InventoryCountStateException extends AuthException {

    public InventoryCountStateException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
