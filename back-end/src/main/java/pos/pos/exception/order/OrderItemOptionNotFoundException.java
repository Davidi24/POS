package pos.pos.exception.order;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class OrderItemOptionNotFoundException extends AuthException {

    public OrderItemOptionNotFoundException() {
        super("Order item option not found", HttpStatus.NOT_FOUND);
    }
}
