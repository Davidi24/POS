package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class BranchAddressNotFoundException extends AuthException {

    public BranchAddressNotFoundException() {
        super("Branch address not found", HttpStatus.NOT_FOUND);
    }
}
