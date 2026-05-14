package pos.pos.security.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "security.jwt")
@Getter
@Setter
public class JwtProperties {
    private String privateKey;
    private String publicKey;
    private Duration accessExpiration;
    private Duration refreshExpiration;
}
