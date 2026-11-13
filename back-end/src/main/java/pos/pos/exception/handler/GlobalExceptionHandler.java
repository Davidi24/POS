package pos.pos.exception.handler;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.server.ResponseStatusException;
import pos.pos.exception.auth.AuthException;

import java.sql.SQLException;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuth(AuthException ex) {
        return ResponseEntity
                .status(ex.getStatus())
                .body(new ErrorResponse(ex.getStatus().value(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));
        if (message.isBlank()) {
            message = ex.getBindingResult().getAllErrors().stream()
                    .map(e -> e.getDefaultMessage() == null ? "Invalid request" : e.getDefaultMessage())
                    .collect(Collectors.joining(", "));
        }
        return error(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleMethodValidation(HandlerMethodValidationException ex) {
        String message = ex.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> result.getMethodParameter().getParameterName() + ": " + error.getDefaultMessage()))
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, message.isBlank() ? "Invalid request" : message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String message = ex.getConstraintViolations().stream()
                .map(violation -> lastPathNode(violation.getPropertyPath().toString()) + ": " + violation.getMessage())
                .sorted()
                .collect(Collectors.joining(", "));
        return error(HttpStatus.BAD_REQUEST, message.isBlank() ? "Invalid request" : message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof pos.pos.config.web.RequestSizeLimitFilter.PayloadTooLargeException) {
                return error(HttpStatus.PAYLOAD_TOO_LARGE, "The request is too large");
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return error(HttpStatus.BAD_REQUEST, "Malformed request body");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "Invalid value for " + ex.getName());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex) {
        return error(HttpStatus.BAD_REQUEST, "Missing required parameter " + ex.getParameterName());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException ex) {
        return error(HttpStatus.BAD_REQUEST, "Missing required part " + ex.getRequestPartName());
    }

    @ExceptionHandler(ServletRequestBindingException.class)
    public ResponseEntity<ErrorResponse> handleBinding(ServletRequestBindingException ex) {
        return error(HttpStatus.BAD_REQUEST, "Invalid request");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "Method " + ex.getMethod() + " is not supported here");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported content type");
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Void> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        // The client refuses JSON, so an error body could not be written either.
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).build();
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "The file is too large");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Access denied");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "Resource not found");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        int status = ex.getStatusCode().value();
        return ResponseEntity
                .status(ex.getStatusCode())
                .body(new ErrorResponse(status, ex.getReason()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        String sqlState = sqlState(ex);
        logger.warn("Rejected a write that breaks a database rule (SQLState {}): {}", sqlState, rootMessage(ex));
        if (sqlState == null) {
            return error(HttpStatus.CONFLICT, "The change conflicts with existing data");
        }
        return switch (sqlState) {
            case "22001" -> error(HttpStatus.BAD_REQUEST, "A value is too long");
            case "22003" -> error(HttpStatus.BAD_REQUEST, "A number is out of range");
            case "22007", "22008" -> error(HttpStatus.BAD_REQUEST, "A date or time is out of range");
            case "22P02", "22023" -> error(HttpStatus.BAD_REQUEST, "A value has the wrong format");
            case "23502" -> error(HttpStatus.BAD_REQUEST, "A required value is missing");
            case "23514" -> error(HttpStatus.BAD_REQUEST, "A value breaks a rule of this record");
            case "23505" -> error(HttpStatus.CONFLICT, "This already exists");
            case "23503" -> error(HttpStatus.CONFLICT, "This is still used by other records, or refers to something that no longer exists");
            case "23P01" -> error(HttpStatus.CONFLICT, "This overlaps with an existing record");
            default -> error(HttpStatus.CONFLICT, "The change conflicts with existing data");
        };
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ErrorResponse> handleConcurrency(ConcurrencyFailureException ex) {
        logger.warn("Concurrent update rejected: {}", rootMessage(ex));
        return error(HttpStatus.CONFLICT, "Someone else changed this at the same time. Refresh and try again.");
    }

    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    public ResponseEntity<ErrorResponse> handleInvalidDataAccess(InvalidDataAccessApiUsageException ex) {
        // Entities check their own invariants before they are written and throw IllegalStateException;
        // Spring wraps those. They describe bad input, not a server fault.
        Throwable domain = domainRuleViolation(ex);
        if (domain != null) {
            return error(HttpStatus.BAD_REQUEST, domain.getMessage());
        }
        return handleGeneric(ex);
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ErrorResponse> handleTransactionSystem(TransactionSystemException ex) {
        Throwable cause = ex.getRootCause();
        if (cause instanceof ConstraintViolationException violation) {
            return handleConstraintViolation(violation);
        }
        Throwable domain = domainRuleViolation(ex);
        if (domain != null) {
            return error(HttpStatus.BAD_REQUEST, domain.getMessage());
        }
        return handleGeneric(ex);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        if (isThrownByEntityRule(ex)) {
            return error(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
        return handleGeneric(ex);
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<Void> handleAsyncTimeout(AsyncRequestTimeoutException ex) {
        // Live streams (SSE) time out on purpose; the client reconnects.
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncNotUsable(AsyncRequestNotUsableException ex) {
        // The client went away while a stream was open; nothing can be written back.
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        logger.error("Unhandled exception", ex);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity
                .status(status)
                .body(new ErrorResponse(status.value(), message));
    }

    private static String sqlState(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if (current instanceof SQLException sql && sql.getSQLState() != null) {
                return sql.getSQLState();
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
    }

    private static String rootMessage(Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getMessage();
    }

    private static Throwable domainRuleViolation(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            if ((current instanceof IllegalStateException || current instanceof IllegalArgumentException)
                    && isThrownByEntityRule(current)) {
                return current;
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return null;
    }

    // Entity invariants live in validateState()/normalizeFields() of classes under pos.pos.
    private static boolean isThrownByEntityRule(Throwable ex) {
        StackTraceElement[] trace = ex.getStackTrace();
        if (trace.length == 0 || ex.getMessage() == null) {
            return false;
        }
        for (int i = 0; i < Math.min(trace.length, 3); i++) {
            String method = trace[i].getMethodName();
            if (trace[i].getClassName().startsWith("pos.pos.")
                    && (method.equals("validateState") || method.equals("normalizeFields") || method.startsWith("validate"))) {
                return true;
            }
        }
        return false;
    }

    private static String lastPathNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
