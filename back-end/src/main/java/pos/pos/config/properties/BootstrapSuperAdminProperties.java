package pos.pos.config.properties;

import jakarta.validation.constraints.AssertTrue;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.StringUtils;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.bootstrap.super-admin")
public class BootstrapSuperAdminProperties {

    private boolean enabled;
    private String email;
    private String username;
    private String password;
    private String firstName;
    private String lastName;

    @AssertTrue(message = "app.bootstrap.super-admin.email must not be blank when bootstrap is enabled")
    public boolean isEmailConfiguredWhenEnabled() {
        return !enabled || StringUtils.hasText(email);
    }

    @AssertTrue(message = "app.bootstrap.super-admin.username must not be blank when bootstrap is enabled")
    public boolean isUsernameConfiguredWhenEnabled() {
        return !enabled || StringUtils.hasText(username);
    }

    @AssertTrue(message = "app.bootstrap.super-admin.password must not be blank when bootstrap is enabled")
    public boolean isPasswordConfiguredWhenEnabled() {
        return !enabled || StringUtils.hasText(password);
    }
}
