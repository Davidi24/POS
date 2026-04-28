package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RestaurantOwnerNotFoundException extends AuthException {

    public RestaurantOwnerNotFoundException() {
        super("Owner user not found", HttpStatus.NOT_FOUND);
    }
}
