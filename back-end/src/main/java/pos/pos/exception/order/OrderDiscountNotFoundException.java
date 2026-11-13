package pos.pos.exception.order;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class OrderDiscountNotFoundException extends AuthException {

    public OrderDiscountNotFoundException() {
        super("Order discount not found", HttpStatus.NOT_FOUND);
    }
}
