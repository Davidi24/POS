package pos.pos.exception.auth;

import org.springframework.http.HttpStatus;

public class InvalidTokenException extends AuthException {

    public InvalidTokenException() {
        super("Invalid token", HttpStatus.UNAUTHORIZED);
    }
}