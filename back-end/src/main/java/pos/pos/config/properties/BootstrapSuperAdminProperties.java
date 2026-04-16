package pos.pos.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.bootstrap.super-admin")
public class BootstrapSuperAdminProperties {

    private String email;
    private String username;
    private String password;
    private String firstName;
    private String lastName;
}
