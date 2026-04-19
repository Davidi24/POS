package pos.pos.exception.role;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RoleManagementNotAllowedException extends AuthException {

    public RoleManagementNotAllowedException() {
        super("You are not allowed to manage this role", HttpStatus.FORBIDDEN);
    }
}
