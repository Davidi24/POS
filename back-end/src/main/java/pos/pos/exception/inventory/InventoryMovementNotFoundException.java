package pos.pos.exception.inventory;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class InventoryMovementNotFoundException extends AuthException {

    public InventoryMovementNotFoundException() {
        super("Inventory movement not found", HttpStatus.NOT_FOUND);
    }
}
