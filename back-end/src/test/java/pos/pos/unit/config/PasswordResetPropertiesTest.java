package pos.pos.unit.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import pos.pos.config.properties.PasswordResetProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PasswordResetProperties")
class PasswordResetPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class
            ))
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("Should bind password reset properties")
    void shouldBindProperties() {
        contextRunner
                .withPropertyValues(
                        "app.auth.password-reset.token-ttl=30m",
                        "app.auth.password-reset.request-cooldown=2m",
                        "app.auth.password-reset.token-pepper=test-reset-pepper",
                        "app.auth.password-reset.reset-path=/reset-password",
                        "app.auth.password-reset.subject=Reset your password"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    PasswordResetProperties properties = context.getBean(PasswordResetProperties.class);
                    assertThat(properties.getTokenTtl()).isEqualTo(Duration.ofMinutes(30));
                    assertThat(properties.getRequestCooldown()).isEqualTo(Duration.ofMinutes(2));
                    assertThat(properties.getTokenPepper()).isEqualTo("test-reset-pepper");
                    assertThat(properties.getResetPath()).isEqualTo("/reset-password");
                    assertThat(properties.getSubject()).isEqualTo("Reset your password");
                });
    }

    @Test
    @DisplayName("Should fail startup when required password reset properties are missing")
    void shouldFailWhenRequiredPropertiesAreMissing() {
        contextRunner
                .withPropertyValues(
                        "app.auth.password-reset.token-ttl=30m",
                        "app.auth.password-reset.request-cooldown=2m",
                        "app.auth.password-reset.reset-path=/reset-password",
                        "app.auth.password-reset.subject=Reset your password"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(PasswordResetProperties.class)
    static class TestConfig {
    }
}
