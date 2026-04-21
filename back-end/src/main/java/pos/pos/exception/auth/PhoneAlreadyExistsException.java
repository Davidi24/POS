package pos.pos.exception.auth;

import org.springframework.http.HttpStatus;

public class PhoneAlreadyExistsException extends AuthException {

    public PhoneAlreadyExistsException() {
        super("Phone already in use", HttpStatus.BAD_REQUEST);
    }
}
