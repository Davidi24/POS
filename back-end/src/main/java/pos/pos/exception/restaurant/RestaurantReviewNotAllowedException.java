package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RestaurantReviewNotAllowedException extends AuthException {

    public RestaurantReviewNotAllowedException() {
        super("You are not allowed to review restaurant registrations", HttpStatus.FORBIDDEN);
    }
}
