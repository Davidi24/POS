package pos.pos.exception.auth;

import org.springframework.http.HttpStatus;

public class UsernameAlreadyExistsException extends AuthException {

    public UsernameAlreadyExistsException() {
        super("Username already in use", HttpStatus.BAD_REQUEST);
    }
}
