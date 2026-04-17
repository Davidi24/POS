package pos.pos.unit.security.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.security.config.SecurityPaths;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SecurityPaths")
class SecurityPathsTest {

    @Test
    @DisplayName("Should keep the expected public auth and documentation endpoints")
    void shouldExposeExpectedPublicPaths() {
        List<String> publicPaths = Arrays.asList(SecurityPaths.PUBLIC);

        assertThat(publicPaths).contains(
                "/auth/web/login",
                "/auth/web/refresh",
                "/auth/device/login",
                "/auth/forgot-password",
                "/auth/reset-password/code",
                "/auth/verify-email",
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/error"
        );
        assertThat(publicPaths).doesNotContain("/roles/assignable", "/auth/me");
        assertThat(publicPaths).doesNotHaveDuplicates();
    }
}
