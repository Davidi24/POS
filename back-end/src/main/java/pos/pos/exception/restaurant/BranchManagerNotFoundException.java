package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class BranchManagerNotFoundException extends AuthException {

    public BranchManagerNotFoundException() {
        super("Branch manager user not found", HttpStatus.BAD_REQUEST);
    }
}
