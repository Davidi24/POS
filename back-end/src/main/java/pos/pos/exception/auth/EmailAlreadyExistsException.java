package pos.pos.exception.auth;

import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends AuthException {

    public EmailAlreadyExistsException() {
        super("Email already in use", HttpStatus.BAD_REQUEST);
    }
}
