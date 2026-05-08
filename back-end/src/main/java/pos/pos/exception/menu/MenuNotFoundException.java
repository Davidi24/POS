package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class MenuNotFoundException extends AuthException {

    public MenuNotFoundException() {
        super("Menu not found", HttpStatus.NOT_FOUND);
    }
}
