package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RestaurantManagementNotAllowedException extends AuthException {

    public RestaurantManagementNotAllowedException() {
        super("You are not allowed to manage this restaurant", HttpStatus.FORBIDDEN);
    }
}
