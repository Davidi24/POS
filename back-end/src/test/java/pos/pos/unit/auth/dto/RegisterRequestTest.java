package pos.pos.unit.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.auth.dto.RegisterRequest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RegisterRequest")
class RegisterRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("Should accept a valid register request")
    void shouldAcceptValidRequest() {
        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(validRequest());

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Should reject usernames with disallowed characters")
    void shouldRejectInvalidUsername() {
        RegisterRequest request = validRequest();
        request.setUsername("cashier@one");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .contains("Username may only contain letters, numbers, dots, underscores, and hyphens");
    }

    @Test
    @DisplayName("Should reject blank required fields")
    void shouldRejectBlankRequiredFields() {
        RegisterRequest request = new RegisterRequest();

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(request);

        assertThat(violations).extracting(ConstraintViolation::getMessage)
                .contains(
                        "Email is required",
                        "Username is required",
                        "Password is required",
                        "First name is required",
                        "Last name is required"
                );
    }

    private RegisterRequest validRequest() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("cashier@pos.local");
        request.setUsername("cashier.one");
        request.setPassword("SecurePass1!");
        request.setFirstName("John");
        request.setLastName("Doe");
        return request;
    }
}
