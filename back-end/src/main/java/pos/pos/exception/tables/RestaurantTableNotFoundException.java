package pos.pos.exception.tables;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RestaurantTableNotFoundException extends AuthException {

    public RestaurantTableNotFoundException() {
        super("Table not found", HttpStatus.NOT_FOUND);
    }
}
