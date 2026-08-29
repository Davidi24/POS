package pos.pos.exception.inventory;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class InventoryCountNotFoundException extends AuthException {

    public InventoryCountNotFoundException() {
        super("Inventory count not found", HttpStatus.NOT_FOUND);
    }
}
