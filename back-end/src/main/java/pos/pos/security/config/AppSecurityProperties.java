package pos.pos.security.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "app.security")
@Getter
@Setter
@Validated
public class AppSecurityProperties {

    @NotEmpty
    private List<String> trustedProxies = new ArrayList<>();

    @Min(50)
    @Max(2048)
    private int maxUserAgentLength = 512;
}