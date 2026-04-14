package pos.pos.unit.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import pos.pos.security.config.AuthCookieProperties;

import static org.assertj.core.api.Assertions.assertThat;

class AuthCookiePropertiesTest {

    private static final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "app.security.trusted-proxies[0]=127.0.0.1",
                    "app.security.max-user-agent-length=512"
            );

    @Nested
    @DisplayName("Spring binding")
    class SpringBindingTests {

        @Test
        @DisplayName("Should bind auth cookie properties through configuration properties scan")
        void shouldBindPropertiesViaConfigurationPropertiesScan() {
            contextRunner
                    .withPropertyValues(
                            "app.security.cookie.refresh-token-name=refresh_token",
                            "app.security.cookie.refresh-token-path=/auth/device",
                            "app.security.cookie.same-site=None",
                            "app.security.cookie.secure=false",
                            "app.security.cookie.domain=example.com"
                    )
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        AuthCookieProperties properties = context.getBean(AuthCookieProperties.class);
                        assertThat(properties.getRefreshTokenName()).isEqualTo("refresh_token");
                        assertThat(properties.getRefreshTokenPath()).isEqualTo("/auth/device");
                        assertThat(properties.getSameSite()).isEqualTo("None");
                        assertThat(properties.isSecure()).isFalse();
                        assertThat(properties.getDomain()).isEqualTo("example.com");
                    });
        }

        @Test
        @DisplayName("Should expose default values when auth cookie properties are not configured")
        void shouldExposeDefaultValuesWhenNotConfigured() {
            contextRunner.run(context -> {
                assertThat(context).hasNotFailed();
                AuthCookieProperties properties = context.getBean(AuthCookieProperties.class);
                assertThat(properties.getRefreshTokenName()).isEqualTo("refreshToken");
                assertThat(properties.getRefreshTokenPath()).isEqualTo("/auth/web");
                assertThat(properties.getSameSite()).isEqualTo("Strict");
                assertThat(properties.isSecure()).isTrue();
                assertThat(properties.getDomain()).isNull();
            });
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConfigurationPropertiesScan(basePackageClasses = AuthCookieProperties.class)
    static class TestConfig {
    }
}
