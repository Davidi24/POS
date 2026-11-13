package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RestaurantSlugAlreadyExistsException extends AuthException {

    public RestaurantSlugAlreadyExistsException() {
        this("Restaurant slug already in use");
    }

    public RestaurantSlugAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
