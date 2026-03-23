package pos.pos.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LoginRequestTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void validLoginRequest_shouldHaveNoViolations() {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("password123")
                .build();

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    void invalidEmail_shouldFail() {
        LoginRequest request = LoginRequest.builder()
                .email("invalid-email")
                .password("password123")
                .build();

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void blankEmail_shouldFail() {
        LoginRequest request = LoginRequest.builder()
                .email("")
                .password("password123")
                .build();

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void blankPassword_shouldFail() {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("")
                .build();

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void shortPassword_shouldFail() {
        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password("short")
                .build();

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
    }

    @Test
    void longPassword_shouldFail() {
        String longPassword = "a".repeat(101);

        LoginRequest request = LoginRequest.builder()
                .email("test@example.com")
                .password(longPassword)
                .build();

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

        assertFalse(violations.isEmpty());
    }
}