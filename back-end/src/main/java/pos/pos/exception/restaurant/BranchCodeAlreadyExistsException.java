package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class BranchCodeAlreadyExistsException extends AuthException {

    public BranchCodeAlreadyExistsException() {
        super("Branch code already in use for this restaurant", HttpStatus.BAD_REQUEST);
    }
}
