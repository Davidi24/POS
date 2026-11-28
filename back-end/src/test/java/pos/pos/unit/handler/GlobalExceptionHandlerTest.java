package pos.pos.unit.handler;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import pos.pos.config.web.RequestSizeLimitFilter;
import pos.pos.exception.auth.EmailAlreadyExistsException;
import pos.pos.exception.handler.ErrorResponse;
import pos.pos.exception.handler.GlobalExceptionHandler;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void handleNoResourceFound_shouldReturnNotFound() {
        ResponseEntity<?> response = handler.handleNoResourceFound(
                new NoResourceFoundException(HttpMethod.GET, "/v3/api-docs")
        );

        assertEquals(404, response.getStatusCode().value());

        ErrorResponse body = (ErrorResponse) response.getBody();
        assertNotNull(body);
        assertEquals(404, body.status());
        assertEquals("Resource not found", body.message());
    }

    @ParameterizedTest(name = "SQLState {0} -> {1}")
    @CsvSource({
            "22001, 400, A value is too long",
            "22003, 400, A number is out of range",
            "22007, 400, A date or time is out of range",
            "22P02, 400, A value has the wrong format",
            "23502, 400, A required value is missing",
            "23514, 400, A value breaks a rule of this record",
            "23505, 409, This already exists",
            "23503, 409, 'This is still used by other records, or refers to something that no longer exists'",
            "23P01, 409, This overlaps with an existing record",
            "40001, 409, The change conflicts with existing data"
    })
    @DisplayName("database rule violations become clear 4xx answers instead of 500")
    void dataIntegrityBySqlState(String sqlState, int status, String message) {
        var ex = new DataIntegrityViolationException("could not execute", new SQLException("boom", sqlState));
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(ex);
        assertThat(response.getStatusCode().value()).isEqualTo(status);
        assertThat(response.getBody().message()).isEqualTo(message);
    }

    @Test
    @DisplayName("a database rule violation without a SQL state is a conflict")
    void dataIntegrityWithoutState() {
        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrity(new DataIntegrityViolationException("x"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("bad ids, missing parameters, wrong methods and media types are 4xx")
    void requestShapeErrors() throws Exception {
        MethodParameter parameter = new MethodParameter(Object.class.getMethod("equals", Object.class), 0);
        var mismatch = new MethodArgumentTypeMismatchException("abc", UUID.class, "orderId", parameter, new IllegalArgumentException());
        assertThat(handler.handleTypeMismatch(mismatch).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleTypeMismatch(mismatch).getBody().message()).isEqualTo("Invalid value for orderId");

        var missing = new MissingServletRequestParameterException("from", "LocalDate");
        assertThat(handler.handleMissingParameter(missing).getBody().message()).isEqualTo("Missing required parameter from");

        var method = new HttpRequestMethodNotSupportedException("TRACE");
        assertThat(handler.handleMethodNotSupported(method).getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

        var media = new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));
        assertThat(handler.handleMediaTypeNotSupported(media).getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);

        assertThat(handler.handleUploadTooLarge(new MaxUploadSizeExceededException(10)).getStatusCode())
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    @DisplayName("a body cut off by the size limit is 413, other unreadable bodies 400")
    void unreadableBodies() {
        var tooLarge = new HttpMessageNotReadableException("I/O error",
                new RequestSizeLimitFilter.PayloadTooLargeException(), new MockHttpInputMessage(new byte[0]));
        assertThat(handler.handleUnreadable(tooLarge).getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        var broken = new HttpMessageNotReadableException("JSON parse error", new MockHttpInputMessage(new byte[0]));
        assertThat(handler.handleUnreadable(broken).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(handler.handleUnreadable(broken).getBody().message()).isEqualTo("Malformed request body");
    }

    @Test
    @DisplayName("two people saving the same record at once get a 409 asking to refresh")
    void optimisticLocking() {
        var ex = new ObjectOptimisticLockingFailureException(Object.class, UUID.randomUUID());
        assertThat(handler.handleConcurrency(ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    static final class FakeEntity {
        void validateState() {
            throw new IllegalStateException("amount must be greater than zero");
        }
    }

    @Test
    @DisplayName("an entity's own rule broken by the input is a 400 with its message; other state errors stay 500")
    void entityRules() {
        IllegalStateException entityRule;
        try {
            new FakeEntity().validateState();
            throw new AssertionError("expected");
        } catch (IllegalStateException ex) {
            entityRule = ex;
        }
        // The fake isn't under pos.pos, so pretend its frame came from an entity there.
        StackTraceElement[] trace = entityRule.getStackTrace();
        trace[0] = new StackTraceElement("pos.pos.payment.entity.Payment", "validateState", "Payment.java", 1);
        entityRule.setStackTrace(trace);

        ResponseEntity<ErrorResponse> wrapped = handler.handleInvalidDataAccess(new InvalidDataAccessApiUsageException("x", entityRule));
        assertThat(wrapped.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(wrapped.getBody().message()).isEqualTo("amount must be greater than zero");
        assertThat(handler.handleIllegalState(entityRule).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        IllegalStateException bug = new IllegalStateException("unexpected");
        assertThat(handler.handleIllegalState(bug).getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(handler.handleIllegalState(bug).getBody().message()).isEqualTo("An unexpected error occurred");
    }

    @Test
    @DisplayName("unexpected errors never leak details")
    void genericHidesDetails() {
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(new RuntimeException("password=secret"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).doesNotContain("secret");
    }
}
