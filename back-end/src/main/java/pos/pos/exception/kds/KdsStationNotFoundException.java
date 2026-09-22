package pos.pos.exception.kds;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class KdsStationNotFoundException extends AuthException {

    public KdsStationNotFoundException() {
        super("KDS station not found", HttpStatus.NOT_FOUND);
    }
}
