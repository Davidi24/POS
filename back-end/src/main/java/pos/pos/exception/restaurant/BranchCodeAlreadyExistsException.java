package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class BranchCodeAlreadyExistsException extends AuthException {

    public BranchCodeAlreadyExistsException() {
        this("Branch code already in use for this restaurant");
    }

    public BranchCodeAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
