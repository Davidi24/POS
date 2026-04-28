package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RestaurantRegistrationNotPendingException extends AuthException {

    public RestaurantRegistrationNotPendingException() {
        super("Only pending restaurant registrations can be reviewed", HttpStatus.BAD_REQUEST);
    }
}
