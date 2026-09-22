package pos.pos.exception.kds;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class KdsTicketNotFoundException extends AuthException {

    public KdsTicketNotFoundException() {
        super("KDS ticket not found", HttpStatus.NOT_FOUND);
    }
}
