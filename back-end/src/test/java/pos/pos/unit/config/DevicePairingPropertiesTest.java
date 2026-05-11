package pos.pos.unit.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.env.ConfigurableEnvironment;
import pos.pos.config.properties.DevicePairingProperties;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DevicePairingProperties")
class DevicePairingPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class
            ))
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("Should bind local profile defaults")
    void shouldBindLocalProfileDefaults() {
        loadYaml("application.yml", "application-local.yml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    DevicePairingProperties properties = context.getBean(DevicePairingProperties.class);
                    assertThat(properties.getTokenPepper())
                            .isEqualTo("change-this-local-dev-device-pairing-token-pepper");
                    assertThat(properties.getDefaultTtl()).isEqualTo(Duration.ofMinutes(15));
                    assertThat(properties.getMaxTtl()).isEqualTo(Duration.ofHours(24));
                });
    }

    @Test
    @DisplayName("Should fail fast when token pepper is missing in prod")
    void shouldFailFastWhenTokenPepperIsMissingInProd() {
        loadYaml("application.yml", "application-prod.yml")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseOf(context.getStartupFailure()))
                            .hasMessageContaining("tokenPepper must be at least 32 characters");
                });
    }

    @Test
    @DisplayName("Should fail validation when max ttl is shorter than default ttl")
    void shouldFailValidationWhenMaxTtlIsShorterThanDefaultTtl() {
        contextRunner
                .withPropertyValues(
                        "app.device.pairing.token-pepper=0123456789abcdef0123456789abcdef",
                        "app.device.pairing.default-ttl=30m",
                        "app.device.pairing.max-ttl=10m"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(rootCauseOf(context.getStartupFailure()))
                            .hasMessageContaining("maxTtl must be greater than or equal to defaultTtl");
                });
    }

    private ApplicationContextRunner loadYaml(String... classpathResources) {
        return contextRunner.withInitializer(context ->
                addYamlPropertySources(context.getEnvironment(), classpathResources)
        );
    }

    private void addYamlPropertySources(ConfigurableEnvironment environment, String... classpathResources) {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (int index = classpathResources.length - 1; index >= 0; index--) {
            Resource resource = new ClassPathResource(classpathResources[index]);
            try {
                List<org.springframework.core.env.PropertySource<?>> sources =
                        loader.load(classpathResources[index], resource);
                for (org.springframework.core.env.PropertySource<?> source : sources) {
                    environment.getPropertySources().addLast(source);
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Failed to load " + classpathResources[index], exception);
            }
        }
    }

    private Throwable rootCauseOf(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(DevicePairingProperties.class)
    static class TestConfig {
    }
}
