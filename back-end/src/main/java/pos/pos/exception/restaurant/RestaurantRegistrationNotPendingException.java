package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RestaurantRegistrationNotPendingException extends AuthException {

    public RestaurantRegistrationNotPendingException() {
        super("This application was already reviewed", HttpStatus.CONFLICT);
    }
}
