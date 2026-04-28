package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RestaurantContactNotFoundException extends AuthException {

    public RestaurantContactNotFoundException() {
        super("Restaurant contact not found", HttpStatus.NOT_FOUND);
    }
}
