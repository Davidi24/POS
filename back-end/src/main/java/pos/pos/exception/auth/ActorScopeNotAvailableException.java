package pos.pos.exception.auth;

import org.springframework.http.HttpStatus;

public class ActorScopeNotAvailableException extends AuthException {

    public ActorScopeNotAvailableException() {
        super("Authenticated actor is not available", HttpStatus.FORBIDDEN);
    }
}
