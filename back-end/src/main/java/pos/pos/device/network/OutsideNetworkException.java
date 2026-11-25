package pos.pos.device.network;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

/** A till, tablet or screen being connected from outside its restaurant's network (403). */
public class OutsideNetworkException extends AuthException {

    public OutsideNetworkException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
