package pos.pos.exception.inventory;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class InventoryLocationNotFoundException extends AuthException {

    public InventoryLocationNotFoundException() {
        super("Inventory location not found", HttpStatus.NOT_FOUND);
    }
}
