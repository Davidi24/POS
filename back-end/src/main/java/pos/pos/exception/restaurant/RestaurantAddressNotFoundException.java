package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RestaurantAddressNotFoundException extends AuthException {

    public RestaurantAddressNotFoundException() {
        super("Restaurant address not found", HttpStatus.NOT_FOUND);
    }
}
