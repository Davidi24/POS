package pos.pos.exception.auth;

import org.springframework.http.HttpStatus;

public class SessionNotFoundException extends AuthException {

    public SessionNotFoundException() {
        super("Session not found", HttpStatus.NOT_FOUND);
    }
}