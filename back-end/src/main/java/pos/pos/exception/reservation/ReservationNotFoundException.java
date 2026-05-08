package pos.pos.exception.reservation;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class ReservationNotFoundException extends AuthException {

    public ReservationNotFoundException() {
        super("Reservation not found", HttpStatus.NOT_FOUND);
    }
}
