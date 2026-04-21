package pos.pos.exception.role;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RoleNotFoundException extends AuthException {

    public RoleNotFoundException() {
        super("Role not found", HttpStatus.BAD_REQUEST);
    }
}