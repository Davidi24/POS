package pos.pos.config.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.frontend")
@Getter
@Setter
@Validated
public class FrontendProperties {

    @NotBlank
    private String baseUrl;
}
