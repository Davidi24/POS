package pos.pos.exception.customer;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class CustomerNotFoundException extends AuthException {

    public CustomerNotFoundException() {
        super("Customer not found", HttpStatus.NOT_FOUND);
    }
}
