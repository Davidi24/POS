package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RestaurantOwnershipChangeNotAllowedException extends AuthException {

    public RestaurantOwnershipChangeNotAllowedException() {
        super("You are not allowed to change restaurant ownership", HttpStatus.FORBIDDEN);
    }
}
