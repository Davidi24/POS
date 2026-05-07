package pos.pos.exception.reservation;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class ReservationNoteNotFoundException extends AuthException {

    public ReservationNoteNotFoundException() {
        super("Reservation note not found", HttpStatus.NOT_FOUND);
    }
}
