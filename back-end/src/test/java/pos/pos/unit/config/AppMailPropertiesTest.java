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
import pos.pos.config.properties.AppMailProperties;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AppMailProperties")
class AppMailPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class
            ))
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("Should use safe default from address from base config")
    void shouldUseSafeDefaultFromAddressFromBaseConfig() {
        loadYaml("application.yml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AppMailProperties.class).getFrom())
                            .isEqualTo("no-reply@pos.local");
                });
    }

    @Test
    @DisplayName("Should bind local profile from address")
    void shouldBindLocalProfileFromAddress() {
        loadYaml("application.yml", "application-local.yml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AppMailProperties.class).getFrom())
                            .isEqualTo("no-reply@pos.local");
                });
    }

    @Test
    @DisplayName("Should fail fast in prod when mail from is missing")
    void shouldFailFastInProdWhenMailFromMissing() {
        loadYaml("application.yml", "application-prod.yml")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable startupFailure = context.getStartupFailure();
                    assertThat(startupFailure).hasMessageContaining("app.mail");
                    assertThat(rootCauseOf(startupFailure))
                            .hasMessageContaining("Binding validation errors on app.mail")
                            .hasMessageContaining("must be a well-formed email address");
                });
    }

    @Test
    @DisplayName("Should bind prod from address when provided")
    void shouldBindProdFromAddressWhenProvided() {
        loadYaml("application.yml", "application-prod.yml")
                .withPropertyValues("MAIL_FROM=no-reply@pos.example")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(AppMailProperties.class).getFrom())
                            .isEqualTo("no-reply@pos.example");
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
    @EnableConfigurationProperties(AppMailProperties.class)
    static class TestConfig {
    }
}
