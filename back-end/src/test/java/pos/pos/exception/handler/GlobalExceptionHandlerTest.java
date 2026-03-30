package pos.pos.exception.handler;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import pos.pos.exception.auth.EmailAlreadyExistsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleAuth_shouldReturnStatusAndMessage() {
        ResponseEntity<?> response = handler.handleAuth(new EmailAlreadyExistsException());

        assertEquals(400, response.getStatusCode().value());

        ErrorResponse body = (ErrorResponse) response.getBody();
        assertNotNull(body);
        assertEquals(400, body.status());
        assertEquals("Email already in use", body.message());
    }
}
