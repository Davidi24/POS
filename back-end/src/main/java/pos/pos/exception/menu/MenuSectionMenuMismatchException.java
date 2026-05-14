package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class MenuSectionMenuMismatchException extends AuthException {

    public MenuSectionMenuMismatchException() {
        super("Menu section does not belong to this menu", HttpStatus.CONFLICT);
    }
}
