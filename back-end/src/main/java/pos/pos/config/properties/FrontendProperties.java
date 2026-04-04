package pos.pos.config.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "app.frontend")
@Getter
@Setter
@Validated
public class FrontendProperties {

    @NotBlank
    private String baseUrl = "http://localhost:3000";
}
