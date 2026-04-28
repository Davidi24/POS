package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RestaurantNotFoundException extends AuthException {

    public RestaurantNotFoundException() {
        super("Restaurant not found", HttpStatus.NOT_FOUND);
    }
}
