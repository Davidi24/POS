package pos.pos.exception.recipe;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;

public class RecipeComponentValidationException extends AuthException {

    public RecipeComponentValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
