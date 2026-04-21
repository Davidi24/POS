package pos.pos.exception.role;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class PermissionNotFoundException extends AuthException {

    public PermissionNotFoundException() {
        super("Permission not found", HttpStatus.BAD_REQUEST);
    }
}
