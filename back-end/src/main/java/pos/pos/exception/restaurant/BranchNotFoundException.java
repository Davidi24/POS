package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class BranchNotFoundException extends AuthException {

    public BranchNotFoundException() {
        super("Branch not found", HttpStatus.NOT_FOUND);
    }
}
