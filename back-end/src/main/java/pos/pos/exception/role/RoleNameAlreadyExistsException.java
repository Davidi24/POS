package pos.pos.exception.role;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RoleNameAlreadyExistsException extends AuthException {

    public RoleNameAlreadyExistsException() {
        super("Role name already in use", HttpStatus.BAD_REQUEST);
    }
}
