package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RestaurantAccessNotAllowedException extends AuthException {

    public RestaurantAccessNotAllowedException() {
        super("You are not allowed to access this restaurant", HttpStatus.FORBIDDEN);
    }
}
