package pos.pos.exception.kds;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class KdsTicketItemNotFoundException extends AuthException {

    public KdsTicketItemNotFoundException() {
        super("KDS ticket item not found", HttpStatus.NOT_FOUND);
    }
}
