package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RestaurantCreationNotAllowedException extends AuthException {

    public RestaurantCreationNotAllowedException() {
        super("You are not allowed to create restaurants", HttpStatus.FORBIDDEN);
    }
}
