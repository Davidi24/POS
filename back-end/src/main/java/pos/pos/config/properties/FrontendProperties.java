package pos.pos.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import pos.pos.auth.enums.ClientLinkTarget;
import jakarta.validation.constraints.NotBlank;

@ConfigurationProperties(prefix = "app.frontend")
@Getter
@Setter
@Validated
public class FrontendProperties {

    @NotBlank
    private String baseUrl;

    private String mobileBaseUrl;

    private String universalBaseUrl;

    private ClientLinkTarget defaultLinkTarget = ClientLinkTarget.UNIVERSAL;

    public String resolveBaseUrl(ClientLinkTarget target) {
        ClientLinkTarget effectiveTarget = target == null ? defaultLinkTarget : target;

        return switch (effectiveTarget) {
            case MOBILE -> StringUtils.hasText(mobileBaseUrl) ? mobileBaseUrl : baseUrl;
            case UNIVERSAL -> StringUtils.hasText(universalBaseUrl) ? universalBaseUrl : baseUrl;
            case WEB -> baseUrl;
        };
    }
}
