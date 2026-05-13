package pos.pos.exception.order;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class OrderNotFoundException extends AuthException {

    public OrderNotFoundException() {
        super("Order not found", HttpStatus.NOT_FOUND);
    }
}
