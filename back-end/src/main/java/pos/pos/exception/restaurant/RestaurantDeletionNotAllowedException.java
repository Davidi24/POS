package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RestaurantDeletionNotAllowedException extends AuthException {

    public RestaurantDeletionNotAllowedException() {
        super("You are not allowed to delete restaurants", HttpStatus.FORBIDDEN);
    }
}
