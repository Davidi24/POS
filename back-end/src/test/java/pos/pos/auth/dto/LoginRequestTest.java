package pos.pos.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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

    /*
     * =========================================
     * Valid Input
     * =========================================
     */

    @Nested
    @DisplayName("Valid input")
    class ValidTests {

        @Test
        @DisplayName("Should pass validation with correct email and password")
        void shouldPassValidation() {
            LoginRequest request = LoginRequest.builder()
                    .email("test@example.com")
                    .password("password123")
                    .build();

            Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should pass when password is exactly minimum length (8)")
        void shouldPass_minPasswordLength() {
            LoginRequest request = LoginRequest.builder()
                    .email("test@example.com")
                    .password("12345678")
                    .build();

            Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

            assertTrue(violations.isEmpty());
        }
    }

    /*
     * =========================================
     * Email Validation
     * =========================================
     */

    @Nested
    @DisplayName("Email validation")
    class EmailTests {

        @Test
        @DisplayName("Should fail for invalid email format")
        void shouldFail_invalidEmail() {
            LoginRequest request = LoginRequest.builder()
                    .email("invalid-email")
                    .password("password123")
                    .build();

            Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

            assertTrue(hasViolation(violations, "email"));
        }

        @Test
        @DisplayName("Should fail for blank email")
        void shouldFail_blankEmail() {
            LoginRequest request = LoginRequest.builder()
                    .email("")
                    .password("password123")
                    .build();

            Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

            assertTrue(hasViolation(violations, "email"));
        }

        @Test
        @DisplayName("Should fail for null email")
        void shouldFail_nullEmail() {
            LoginRequest request = LoginRequest.builder()
                    .email(null)
                    .password("password123")
                    .build();

            Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

            assertTrue(hasViolation(violations, "email"));
        }
    }

    /*
     * =========================================
     * Password Validation
     * =========================================
     */

    @Nested
    @DisplayName("Password validation")
    class PasswordTests {

        @Test
        @DisplayName("Should fail for blank password")
        void shouldFail_blankPassword() {
            LoginRequest request = LoginRequest.builder()
                    .email("test@example.com")
                    .password("")
                    .build();

            Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

            assertTrue(hasViolation(violations, "password"));
        }

        @Test
        @DisplayName("Should fail for null password")
        void shouldFail_nullPassword() {
            LoginRequest request = LoginRequest.builder()
                    .email("test@example.com")
                    .password(null)
                    .build();

            Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

            assertTrue(hasViolation(violations, "password"));
        }

        @Test
        @DisplayName("Should fail for password shorter than 8")
        void shouldFail_shortPassword() {
            LoginRequest request = LoginRequest.builder()
                    .email("test@example.com")
                    .password("short")
                    .build();

            Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

            assertTrue(hasViolation(violations, "password"));
        }

        @Test
        @DisplayName("Should fail for password longer than 100")
        void shouldFail_longPassword() {
            String longPassword = "a".repeat(101);

            LoginRequest request = LoginRequest.builder()
                    .email("test@example.com")
                    .password(longPassword)
                    .build();

            Set<ConstraintViolation<LoginRequest>> violations = validator.validate(request);

            assertTrue(hasViolation(violations, "password"));
        }
    }

    /*
     * =========================================
     * Helper
     * =========================================
     */

    private boolean hasViolation(Set<ConstraintViolation<LoginRequest>> violations, String field) {
        return violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals(field));
    }
}