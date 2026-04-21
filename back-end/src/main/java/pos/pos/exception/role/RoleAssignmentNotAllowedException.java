package pos.pos.exception.role;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RoleAssignmentNotAllowedException extends AuthException {

    public RoleAssignmentNotAllowedException() {
        super("You are not allowed to assign this role", HttpStatus.FORBIDDEN);
    }
}