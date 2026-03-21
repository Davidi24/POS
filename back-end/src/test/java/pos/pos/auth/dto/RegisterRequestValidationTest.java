package pos.pos.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegisterRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("RegisterRequest is valid when all fields are correct")
    void shouldHaveNoViolationsWhenRequestIsValid() {
        RegisterRequest request = validRequest();

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("RegisterRequest returns violation when email is null")
    void shouldReturnViolationWhenEmailIsNull() {
        RegisterRequest request = validRequest();
        request.setEmail(null);

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("Email is required", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("RegisterRequest returns violation when email is blank")
    void shouldReturnViolationWhenEmailIsBlank() {
        RegisterRequest request = validRequest();
        request.setEmail("");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertTrue(
                violations.stream()
                        .anyMatch(v ->
                                v.getPropertyPath().toString().equals("email")
                                        && v.getMessage().equals("Email is required")
                        )
        );
    }

    @Test
    @DisplayName("RegisterRequest returns violation when email format is invalid")
    void shouldReturnViolationWhenEmailFormatIsInvalid() {
        RegisterRequest request = validRequest();
        request.setEmail("invalid-email");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("Invalid email format", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("RegisterRequest returns violation when password is null")
    void shouldReturnViolationWhenPasswordIsNull() {
        RegisterRequest request = validRequest();
        request.setPassword(null);

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("Password is required", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("RegisterRequest returns violation when password is blank")
    void shouldReturnViolationWhenPasswordIsBlank() {
        RegisterRequest request = validRequest();
        request.setPassword("");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertEquals(2, violations.size());
    }

    @Test
    @DisplayName("RegisterRequest returns violation when password is shorter than 8 characters")
    void shouldReturnViolationWhenPasswordIsTooShort() {
        RegisterRequest request = validRequest();
        request.setPassword("1234567");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("Password must be between 8 and 100 characters", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("RegisterRequest returns violation when password is longer than 100 characters")
    void shouldReturnViolationWhenPasswordIsTooLong() {
        RegisterRequest request = validRequest();
        request.setPassword("a".repeat(101));

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("Password must be between 8 and 100 characters", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("RegisterRequest returns violation when first name is null")
    void shouldReturnViolationWhenFirstNameIsNull() {
        RegisterRequest request = validRequest();
        request.setFirstName(null);

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("First name is required", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("RegisterRequest returns violation when first name is blank")
    void shouldReturnViolationWhenFirstNameIsBlank() {
        RegisterRequest request = validRequest();
        request.setFirstName("");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("First name is required", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("RegisterRequest returns violation when first name is longer than 50 characters")
    void shouldReturnViolationWhenFirstNameIsTooLong() {
        RegisterRequest request = validRequest();
        request.setFirstName("a".repeat(51));

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("First name must be at most 50 characters", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("RegisterRequest returns violation when last name is null")
    void shouldReturnViolationWhenLastNameIsNull() {
        RegisterRequest request = validRequest();
        request.setLastName(null);

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("Last name is required", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("RegisterRequest returns violation when last name is blank")
    void shouldReturnViolationWhenLastNameIsBlank() {
        RegisterRequest request = validRequest();
        request.setLastName("");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("Last name is required", violations.iterator().next().getMessage());
    }

    @Test
    @DisplayName("RegisterRequest returns violation when last name is longer than 50 characters")
    void shouldReturnViolationWhenLastNameIsTooLong() {
        RegisterRequest request = validRequest();
        request.setLastName("a".repeat(51));

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertEquals(1, violations.size());
        assertEquals("Last name must be at most 50 characters", violations.iterator().next().getMessage());
    }

    private RegisterRequest validRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("john@example.com");
        request.setPassword("Password123");
        request.setFirstName("John");
        request.setLastName("Doe");
        return request;
    }
}