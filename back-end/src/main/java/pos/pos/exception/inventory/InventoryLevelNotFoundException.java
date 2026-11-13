package pos.pos.exception.inventory;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class InventoryLevelNotFoundException extends AuthException {

    public InventoryLevelNotFoundException() {
        super("No stock level recorded for this item at this location", HttpStatus.NOT_FOUND);
    }
}
