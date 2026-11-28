package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class MenuSectionNotFoundException extends AuthException {

    public MenuSectionNotFoundException() {
        super("Menu section not found", HttpStatus.NOT_FOUND);
    }
}
