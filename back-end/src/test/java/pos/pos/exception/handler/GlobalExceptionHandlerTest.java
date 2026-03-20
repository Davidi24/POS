package pos.pos.exception.handler;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import pos.pos.exception.auth.EmailAlreadyExistsException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleAuth_shouldReturnStatusAndMessage() {
        ResponseEntity<?> response = handler.handleAuth(new EmailAlreadyExistsException());

        assertEquals(400, response.getStatusCode().value());
        assertEquals("Email already in use", response.getBody());
    }
}
