package pos.pos.unit.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.config.properties.PasswordResetProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PasswordResetProperties")
class PasswordResetPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("the link address must be a real http(s) address, so a missing PUBLIC_BASE_URL stops production from starting")
    void shouldRequireRealLinkAddress() {
        assertThat(valid("https://api.pos.example")).isTrue();
        assertThat(valid("http://localhost:8080")).isTrue();
        assertThat(valid("")).isTrue();
        assertThat(valid(null)).isTrue();
        assertThat(valid("${PUBLIC_BASE_URL}")).isFalse();
        assertThat(valid("https://${PUBLIC_BASE_URL}")).isFalse();
        assertThat(valid("ftp://files.pos.example")).isFalse();
        assertThat(valid("api.pos.example")).isFalse();
        assertThat(valid("https://api pos.example")).isFalse();
    }

    private boolean valid(String linkBaseUrl) {
        PasswordResetProperties properties = new PasswordResetProperties();
        properties.setTokenTtl(Duration.ofMinutes(30));
        properties.setRequestCooldown(Duration.ofMinutes(1));
        properties.setTokenPepper("pepper");
        properties.setResetPath("/public/account/password");
        properties.setSubject("Reset");
        properties.setLinkBaseUrl(linkBaseUrl);
        return validator.validate(properties).isEmpty();
    }
}
