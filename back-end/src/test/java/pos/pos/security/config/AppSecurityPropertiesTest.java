package pos.pos.security.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AppSecurityPropertiesTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Nested
    @DisplayName("Valid configuration")
    class ValidTests {

        @Test
        @DisplayName("Should pass validation with valid values")
        void shouldPassValidation() {
            AppSecurityProperties properties = properties(List.of("127.0.0.1"), 512);

            Set<ConstraintViolation<AppSecurityProperties>> violations = validator.validate(properties);

            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should pass at minimum User-Agent boundary")
        void shouldPassAtMinBoundary() {
            AppSecurityProperties properties = properties(List.of("127.0.0.1"), 50);

            Set<ConstraintViolation<AppSecurityProperties>> violations = validator.validate(properties);

            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Should pass at maximum User-Agent boundary")
        void shouldPassAtMaxBoundary() {
            AppSecurityProperties properties = properties(List.of("127.0.0.1"), 2048);

            Set<ConstraintViolation<AppSecurityProperties>> violations = validator.validate(properties);

            assertTrue(violations.isEmpty());
        }
    }

    @Nested
    @DisplayName("Invalid configuration")
    class InvalidTests {

        @Test
        @DisplayName("Should fail when trusted proxies are empty")
        void shouldFailWhenTrustedProxiesEmpty() {
            AppSecurityProperties properties = properties(List.of(), 512);

            Set<ConstraintViolation<AppSecurityProperties>> violations = validator.validate(properties);

            assertTrue(hasViolation(violations, "trustedProxies"));
        }

        @Test
        @DisplayName("Should fail when trusted proxies are null")
        void shouldFailWhenTrustedProxiesNull() {
            AppSecurityProperties properties = properties(null, 512);

            Set<ConstraintViolation<AppSecurityProperties>> violations = validator.validate(properties);

            assertTrue(hasViolation(violations, "trustedProxies"));
        }

        @Test
        @DisplayName("Should fail when max User-Agent length is below minimum")
        void shouldFailWhenMaxUserAgentTooLow() {
            AppSecurityProperties properties = properties(List.of("127.0.0.1"), 49);

            Set<ConstraintViolation<AppSecurityProperties>> violations = validator.validate(properties);

            assertTrue(hasViolation(violations, "maxUserAgentLength"));
        }

        @Test
        @DisplayName("Should fail when max User-Agent length is above maximum")
        void shouldFailWhenMaxUserAgentTooHigh() {
            AppSecurityProperties properties = properties(List.of("127.0.0.1"), 2049);

            Set<ConstraintViolation<AppSecurityProperties>> violations = validator.validate(properties);

            assertTrue(hasViolation(violations, "maxUserAgentLength"));
        }
    }

    private AppSecurityProperties properties(List<String> trustedProxies, int maxUserAgentLength) {
        AppSecurityProperties properties = new AppSecurityProperties();
        properties.setTrustedProxies(trustedProxies);
        properties.setMaxUserAgentLength(maxUserAgentLength);
        return properties;
    }

    private boolean hasViolation(Set<ConstraintViolation<AppSecurityProperties>> violations, String field) {
        return violations.stream()
                .anyMatch(v -> v.getPropertyPath().toString().equals(field));
    }
}
