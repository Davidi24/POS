package pos.pos.unit.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import pos.pos.auth.enums.SmsDeliveryMode;

import java.io.IOException;
import pos.pos.config.properties.SmsAuthProperties;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SmsAuthProperties")
class SmsAuthPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class
            ))
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("Should disable SMS authentication by default")
    void shouldDisableSmsByDefault() {
        loadYaml("application.yml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SmsAuthProperties properties = context.getBean(SmsAuthProperties.class);
                    assertThat(properties.getDeliveryMode()).isEqualTo(SmsDeliveryMode.DISABLED);
                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(context.getEnvironment().getProperty("spring.profiles.default")).isNull();
                    assertThat(context.getEnvironment().getProperty("app.storage.floor-plans.provider")).isEqualTo("disabled");
                });
    }

    @Test
    @DisplayName("Should enable log-only SMS only in the local profile")
    void shouldEnableLogOnlySmsInLocalProfile() {
        loadYaml("application.yml", "application-local.yml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SmsAuthProperties properties = context.getBean(SmsAuthProperties.class);
                    assertThat(properties.getDeliveryMode()).isEqualTo(SmsDeliveryMode.LOG_ONLY);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(context.getEnvironment().getProperty("app.storage.floor-plans.provider")).isEqualTo("local");
                });
    }

    @Test
    @DisplayName("Should keep SMS disabled in the production profile by default")
    void shouldDisableSmsInProductionProfile() {
        loadYaml("application.yml", "application-prod.yml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SmsAuthProperties properties = context.getBean(SmsAuthProperties.class);
                    assertThat(properties.getDeliveryMode()).isEqualTo(SmsDeliveryMode.DISABLED);
                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(context.getEnvironment().getProperty("app.storage.floor-plans.provider")).isEqualTo("disabled");
                });
    }

    @Test
    @DisplayName("Should bind SMS auth properties and expose enabled flag")
    void shouldBindProperties() {
        contextRunner
                .withPropertyValues(
                        "app.auth.sms.delivery-mode=LOG_ONLY",
                        "app.auth.sms.password-reset-code-ttl=15m",
                        "app.auth.sms.phone-verification-code-ttl=10m",
                        "app.auth.sms.request-cooldown=1m",
                        "app.auth.sms.daily-request-limit=12",
                        "app.auth.sms.code-length=6",
                        "app.auth.sms.max-attempts=4",
                        "app.auth.sms.code-pepper=test-sms-pepper",
                        "app.auth.sms.restricted-password-reset-role-codes[0]=SUPER_ADMIN",
                        "app.auth.sms.restricted-password-reset-role-codes[1]=OWNER"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SmsAuthProperties properties = context.getBean(SmsAuthProperties.class);
                    assertThat(properties.getDeliveryMode()).isEqualTo(SmsDeliveryMode.LOG_ONLY);
                    assertThat(properties.getPasswordResetCodeTtl()).isEqualTo(Duration.ofMinutes(15));
                    assertThat(properties.getPhoneVerificationCodeTtl()).isEqualTo(Duration.ofMinutes(10));
                    assertThat(properties.getRequestCooldown()).isEqualTo(Duration.ofMinutes(1));
                    assertThat(properties.getDailyRequestLimit()).isEqualTo(12);
                    assertThat(properties.getCodeLength()).isEqualTo(6);
                    assertThat(properties.getMaxAttempts()).isEqualTo(4);
                    assertThat(properties.getCodePepper()).isEqualTo("test-sms-pepper");
                    assertThat(properties.getRestrictedPasswordResetRoleCodes()).isEqualTo(List.of("SUPER_ADMIN", "OWNER"));
                    assertThat(properties.isEnabled()).isTrue();
                });
    }

    @Test
    @DisplayName("Should disable SMS auth when delivery mode is disabled")
    void shouldDisableWhenDeliveryModeDisabled() {
        contextRunner
                .withPropertyValues(
                        "app.auth.sms.delivery-mode=DISABLED",
                        "app.auth.sms.code-pepper=test-sms-pepper"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(SmsAuthProperties.class).isEnabled()).isFalse();
                });
    }

    @Test
    @DisplayName("Should fail startup when SMS code length is outside the allowed range")
    void shouldFailWhenCodeLengthIsInvalid() {
        contextRunner
                .withPropertyValues(
                        "app.auth.sms.delivery-mode=LOG_ONLY",
                        "app.auth.sms.code-length=3",
                        "app.auth.sms.code-pepper=test-sms-pepper"
                )
                .run(context -> assertThat(context).hasFailed());
    }

    private ApplicationContextRunner loadYaml(String... classpathResources) {
        return contextRunner.withInitializer(context -> {
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            for (int index = classpathResources.length - 1; index >= 0; index--) {
                Resource resource = new ClassPathResource(classpathResources[index]);
                try {
                    loader.load(classpathResources[index], resource).forEach(source ->
                            context.getEnvironment().getPropertySources().addLast(source));
                } catch (IOException exception) {
                    throw new IllegalStateException("Failed to load " + classpathResources[index], exception);
                }
            }
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SmsAuthProperties.class)
    static class TestConfig {
    }
}
