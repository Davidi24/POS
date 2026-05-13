package pos.pos.exception.menu;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class MenuVariantNotFoundException extends AuthException {

    public MenuVariantNotFoundException() {
        super("Menu variant not found", HttpStatus.NOT_FOUND);
    }
}
