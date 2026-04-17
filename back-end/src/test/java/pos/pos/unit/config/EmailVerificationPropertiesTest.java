package pos.pos.unit.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import pos.pos.config.properties.EmailVerificationProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailVerificationProperties")
class EmailVerificationPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class
            ))
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("Should bind email verification properties")
    void shouldBindProperties() {
        contextRunner
                .withPropertyValues(
                        "app.auth.email-verification.token-ttl=24h",
                        "app.auth.email-verification.request-cooldown=5m",
                        "app.auth.email-verification.token-pepper=test-email-pepper",
                        "app.auth.email-verification.verify-path=/verify-email",
                        "app.auth.email-verification.subject=Verify your email"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    EmailVerificationProperties properties = context.getBean(EmailVerificationProperties.class);
                    assertThat(properties.getTokenTtl()).isEqualTo(Duration.ofHours(24));
                    assertThat(properties.getRequestCooldown()).isEqualTo(Duration.ofMinutes(5));
                    assertThat(properties.getTokenPepper()).isEqualTo("test-email-pepper");
                    assertThat(properties.getVerifyPath()).isEqualTo("/verify-email");
                    assertThat(properties.getSubject()).isEqualTo("Verify your email");
                });
    }

    @Test
    @DisplayName("Should fail startup when required email verification properties are missing")
    void shouldFailWhenRequiredPropertiesAreMissing() {
        contextRunner
                .withPropertyValues(
                        "app.auth.email-verification.token-ttl=24h",
                        "app.auth.email-verification.request-cooldown=5m",
                        "app.auth.email-verification.verify-path=/verify-email",
                        "app.auth.email-verification.subject=Verify your email"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(EmailVerificationProperties.class)
    static class TestConfig {
    }
}
