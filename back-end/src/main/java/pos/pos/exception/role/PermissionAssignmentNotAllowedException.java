package pos.pos.exception.role;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class PermissionAssignmentNotAllowedException extends AuthException {

    public PermissionAssignmentNotAllowedException() {
        super("You are not allowed to assign one or more permissions", HttpStatus.FORBIDDEN);
    }
}
