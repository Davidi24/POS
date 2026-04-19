package pos.pos.exception.role;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RoleCodeAlreadyExistsException extends AuthException {

    public RoleCodeAlreadyExistsException() {
        super("Role code already in use", HttpStatus.BAD_REQUEST);
    }
}
