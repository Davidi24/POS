package pos.pos.exception.auth;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends AuthException {

    public TooManyRequestsException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
    }
}