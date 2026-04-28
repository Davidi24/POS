package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RestaurantSlugAlreadyExistsException extends AuthException {

    public RestaurantSlugAlreadyExistsException() {
        super("Restaurant slug already in use", HttpStatus.BAD_REQUEST);
    }
}
