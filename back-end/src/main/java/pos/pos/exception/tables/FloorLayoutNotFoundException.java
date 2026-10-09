package pos.pos.exception.tables;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class FloorLayoutNotFoundException extends AuthException {

    public FloorLayoutNotFoundException() {
        super("Floor layout not found", HttpStatus.NOT_FOUND);
    }
}