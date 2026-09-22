package pos.pos.exception.inventory;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class InventoryItemNotFoundException extends AuthException {

    public InventoryItemNotFoundException() {
        super("Inventory item not found", HttpStatus.NOT_FOUND);
    }
}
