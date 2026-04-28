package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RestaurantBrandingNotFoundException extends AuthException {

    public RestaurantBrandingNotFoundException() {
        super("Restaurant branding not found", HttpStatus.NOT_FOUND);
    }
}
