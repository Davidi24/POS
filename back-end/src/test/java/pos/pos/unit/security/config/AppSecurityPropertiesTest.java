package pos.pos.unit.security.config;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import pos.pos.security.config.AppSecurityProperties;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppSecurityPropertiesTest {

    private static Validator validator;
    private static final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class
            ))
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "app.security.cookie.refresh-token-name=refreshToken",
                    "app.security.cookie.refresh-token-path=/auth/web",
                    "app.security.cookie.same-site=Strict",
                    "app.security.cookie.secure=true"
            );

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

    @Nested
    @DisplayName("Spring binding")
    class SpringBindingTests {

        @Test
        @DisplayName("Should bind valid app.security properties")
        void shouldBindValidProperties() {
            contextRunner
                    .withPropertyValues(
                            "app.security.trusted-proxies[0]=127.0.0.1",
                            "app.security.trusted-proxies[1]=::1",
                            "app.security.max-user-agent-length=512"
                    )
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        AppSecurityProperties properties = context.getBean(AppSecurityProperties.class);
                        assertThat(properties.getTrustedProxies()).containsExactly("127.0.0.1", "::1");
                        assertThat(properties.getMaxUserAgentLength()).isEqualTo(512);
                    });
        }

        @Test
        @DisplayName("Should fail startup when trusted proxies are missing")
        void shouldFailStartupWhenTrustedProxiesMissing() {
            contextRunner
                    .withPropertyValues("app.security.max-user-agent-length=512")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertTrue(causeMessagesContain(context.getStartupFailure(), "trustedProxies"));
                    });
        }

        @Test
        @DisplayName("Should fail startup when max User-Agent length is below minimum")
        void shouldFailStartupWhenMaxUserAgentTooLow() {
            contextRunner
                    .withPropertyValues(
                            "app.security.trusted-proxies[0]=127.0.0.1",
                            "app.security.max-user-agent-length=49"
                    )
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertTrue(causeMessagesContain(context.getStartupFailure(), "maxUserAgentLength"));
                    });
        }

        @Test
        @DisplayName("Should fail startup when max User-Agent length is above maximum")
        void shouldFailStartupWhenMaxUserAgentTooHigh() {
            contextRunner
                    .withPropertyValues(
                            "app.security.trusted-proxies[0]=127.0.0.1",
                            "app.security.max-user-agent-length=2049"
                    )
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertTrue(causeMessagesContain(context.getStartupFailure(), "maxUserAgentLength"));
                    });
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

    private boolean causeMessagesContain(Throwable throwable, String expectedText) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(expectedText)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Configuration(proxyBeanMethods = false)
    @ConfigurationPropertiesScan(basePackageClasses = AppSecurityProperties.class)
    static class TestConfig {
    }
}
