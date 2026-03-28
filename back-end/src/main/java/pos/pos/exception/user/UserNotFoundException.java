package pos.pos.exception.user;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class UserNotFoundException extends AuthException {

    public UserNotFoundException() {
        super("User not found", HttpStatus.NOT_FOUND);
    }
}