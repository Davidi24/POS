package pos.pos.exception.recipe;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RecipeNotFoundException extends AuthException {

    public RecipeNotFoundException() {
        super("Recipe not found", HttpStatus.NOT_FOUND);
    }
}
