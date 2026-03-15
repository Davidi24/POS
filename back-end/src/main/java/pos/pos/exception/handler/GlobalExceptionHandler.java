package pos.pos.exception.handler;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pos.pos.exception.auth.AuthException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<?> handleAuth(AuthException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(ex.getMessage());
    }
}