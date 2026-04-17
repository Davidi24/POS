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
import pos.pos.config.properties.BootstrapSuperAdminProperties;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BootstrapSuperAdminProperties")
class BootstrapSuperAdminPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class
            ))
            .withUserConfiguration(TestConfig.class);

    @Test
    @DisplayName("Should keep bootstrap disabled by default in base config")
    void shouldKeepBootstrapDisabledByDefaultInBaseConfig() {
        loadYaml("application.yml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    BootstrapSuperAdminProperties properties = context.getBean(BootstrapSuperAdminProperties.class);
                    assertThat(properties.isEnabled()).isFalse();
                    assertThat(properties.getEmail()).isNull();
                });
    }

    @Test
    @DisplayName("Should bind local bootstrap credentials")
    void shouldBindLocalBootstrapCredentials() {
        loadYaml("application.yml", "application-local.yml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    BootstrapSuperAdminProperties properties = context.getBean(BootstrapSuperAdminProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getEmail()).isEqualTo("admin@pos.local");
                    assertThat(properties.getUsername()).isEqualTo("admin");
                });
    }

    @Test
    @DisplayName("Should allow prod startup when bootstrap is disabled")
    void shouldAllowProdStartupWhenBootstrapIsDisabled() {
        loadYaml("application.yml", "application-prod.yml")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(BootstrapSuperAdminProperties.class).isEnabled()).isFalse();
                });
    }

    @Test
    @DisplayName("Should fail fast when prod bootstrap is enabled without credentials")
    void shouldFailFastWhenProdBootstrapIsEnabledWithoutCredentials() {
        loadYaml("application.yml", "application-prod.yml")
                .withPropertyValues("BOOTSTRAP_SUPER_ADMIN_ENABLED=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    Throwable startupFailure = context.getStartupFailure();
                    assertThat(startupFailure).hasMessageContaining("app.bootstrap.super-admin");
                    assertThat(rootCauseOf(startupFailure))
                            .hasMessageContaining("must not be blank when bootstrap is enabled");
                });
    }

    @Test
    @DisplayName("Should bind prod bootstrap credentials when explicitly enabled")
    void shouldBindProdBootstrapCredentialsWhenExplicitlyEnabled() {
        loadYaml("application.yml", "application-prod.yml")
                .withPropertyValues(
                        "BOOTSTRAP_SUPER_ADMIN_ENABLED=true",
                        "BOOTSTRAP_SUPER_ADMIN_EMAIL=owner@example.com",
                        "BOOTSTRAP_SUPER_ADMIN_USERNAME=owner",
                        "BOOTSTRAP_SUPER_ADMIN_PASSWORD=StrongPass123!"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    BootstrapSuperAdminProperties properties = context.getBean(BootstrapSuperAdminProperties.class);
                    assertThat(properties.isEnabled()).isTrue();
                    assertThat(properties.getEmail()).isEqualTo("owner@example.com");
                    assertThat(properties.getUsername()).isEqualTo("owner");
                    assertThat(properties.getPassword()).isEqualTo("StrongPass123!");
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
    @EnableConfigurationProperties(BootstrapSuperAdminProperties.class)
    static class TestConfig {
    }
}
