package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RestaurantCodeAlreadyExistsException extends AuthException {

    public RestaurantCodeAlreadyExistsException() {
        super("Restaurant code already in use", HttpStatus.BAD_REQUEST);
    }
}
