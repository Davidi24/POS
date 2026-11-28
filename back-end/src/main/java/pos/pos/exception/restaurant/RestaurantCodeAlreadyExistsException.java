package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RestaurantCodeAlreadyExistsException extends AuthException {

    public RestaurantCodeAlreadyExistsException() {
        this("Restaurant code already in use");
    }

    public RestaurantCodeAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
