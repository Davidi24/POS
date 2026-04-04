package pos.pos.exception.user;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class UserManagementNotAllowedException extends AuthException {

    public UserManagementNotAllowedException() {
        super("You are not allowed to manage this user", HttpStatus.FORBIDDEN);
    }
}
