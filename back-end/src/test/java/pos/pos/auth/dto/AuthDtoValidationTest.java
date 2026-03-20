package pos.pos.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pos.pos.support.AuthTestDataFactory;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthDtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void registerRequest_validationRules() {
        RegisterRequest valid = AuthTestDataFactory.validRegisterRequest();
        assertTrue(validator.validate(valid).isEmpty());

        RegisterRequest invalid = new RegisterRequest();
        invalid.setEmail("invalid");
        invalid.setPassword("123");
        invalid.setFirstName("x".repeat(51));
        invalid.setLastName("");

        Set<ConstraintViolation<RegisterRequest>> violations = validator.validate(invalid);
        assertFalse(violations.isEmpty());
        assertTrue(hasViolation(violations, "email"));
        assertTrue(hasViolation(violations, "password"));
        assertTrue(hasViolation(violations, "firstName"));
        assertTrue(hasViolation(violations, "lastName"));
    }

    @Test
    void loginRequest_validationRules() {
        LoginRequest valid = AuthTestDataFactory.validLoginRequest();
        assertTrue(validator.validate(valid).isEmpty());

        LoginRequest invalid = LoginRequest.builder()
                .email("bad")
                .password("123")
                .build();

        Set<ConstraintViolation<LoginRequest>> violations = validator.validate(invalid);
        assertTrue(hasViolation(violations, "email"));
        assertTrue(hasViolation(violations, "password"));
    }

    @Test
    void changePasswordRequest_validationRules() {
        ChangePasswordRequest valid = AuthTestDataFactory.validChangePasswordRequest();
        assertTrue(validator.validate(valid).isEmpty());

        ChangePasswordRequest invalid = new ChangePasswordRequest();
        invalid.setCurrentPassword("123");
        invalid.setNewPassword("");

        Set<ConstraintViolation<ChangePasswordRequest>> violations = validator.validate(invalid);
        assertTrue(hasViolation(violations, "currentPassword"));
        assertTrue(hasViolation(violations, "newPassword"));
    }

    @Test
    void forgotPasswordRequest_validationRules() {
        ForgotPasswordRequest valid = AuthTestDataFactory.validForgotPasswordRequest();
        assertTrue(validator.validate(valid).isEmpty());

        ForgotPasswordRequest invalid = new ForgotPasswordRequest();
        invalid.setEmail("bad");

        assertTrue(hasViolation(validator.validate(invalid), "email"));
    }

    @Test
    void refreshTokenRequest_validationRules() {
        RefreshTokenRequest valid = AuthTestDataFactory.validRefreshTokenRequest();
        assertTrue(validator.validate(valid).isEmpty());

        RefreshTokenRequest invalid = RefreshTokenRequest.builder()
                .refreshToken("")
                .build();

        assertTrue(hasViolation(validator.validate(invalid), "refreshToken"));
    }

    @Test
    void resetPasswordRequest_validationRules() {
        ResetPasswordRequest valid = AuthTestDataFactory.validResetPasswordRequest();
        assertTrue(validator.validate(valid).isEmpty());

        ResetPasswordRequest invalid = new ResetPasswordRequest();
        invalid.setToken("");
        invalid.setNewPassword("123");

        Set<ConstraintViolation<ResetPasswordRequest>> violations = validator.validate(invalid);
        assertTrue(hasViolation(violations, "token"));
        assertTrue(hasViolation(violations, "newPassword"));
    }

    @Test
    void resendVerificationRequest_validationRules() {
        ResendVerificationRequest valid = AuthTestDataFactory.validResendVerificationRequest();
        assertTrue(validator.validate(valid).isEmpty());

        ResendVerificationRequest invalid = new ResendVerificationRequest();
        invalid.setEmail("bad");

        assertTrue(hasViolation(validator.validate(invalid), "email"));
    }

    @Test
    void verifyEmailRequest_validationRules() {
        VerifyEmailRequest valid = AuthTestDataFactory.validVerifyEmailRequest();
        assertTrue(validator.validate(valid).isEmpty());

        VerifyEmailRequest invalid = new VerifyEmailRequest();
        invalid.setToken("");

        assertTrue(hasViolation(validator.validate(invalid), "token"));
    }

    private boolean hasViolation(Set<? extends ConstraintViolation<?>> violations, String field) {
        return violations.stream()
                .anyMatch(violation -> violation.getPropertyPath().toString().equals(field));
    }
}
