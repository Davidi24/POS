package pos.pos.exception.restaurant;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class BranchContactNotFoundException extends AuthException {

    public BranchContactNotFoundException() {
        super("Branch contact not found", HttpStatus.NOT_FOUND);
    }
}
