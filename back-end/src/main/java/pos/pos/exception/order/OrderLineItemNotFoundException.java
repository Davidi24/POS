package pos.pos.exception.order;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class OrderLineItemNotFoundException extends AuthException {

    public OrderLineItemNotFoundException() {
        super("Order line item not found", HttpStatus.NOT_FOUND);
    }
}
