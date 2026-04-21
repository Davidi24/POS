package pos.pos.unit.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.auth.enums.ClientLinkTarget;
import pos.pos.config.properties.FrontendProperties;

import static org.assertj.core.api.Assertions.assertThat;

class FrontendPropertiesTest {

    @Test
    @DisplayName("Should resolve target-specific URLs when configured")
    void shouldResolveTargetSpecificUrlsWhenConfigured() {
        FrontendProperties properties = new FrontendProperties();
        properties.setBaseUrl("https://web.pos.local");
        properties.setMobileBaseUrl("pos://reset");
        properties.setUniversalBaseUrl("https://links.pos.local");

        assertThat(properties.resolveBaseUrl(ClientLinkTarget.WEB)).isEqualTo("https://web.pos.local");
        assertThat(properties.resolveBaseUrl(ClientLinkTarget.MOBILE)).isEqualTo("pos://reset");
        assertThat(properties.resolveBaseUrl(ClientLinkTarget.UNIVERSAL)).isEqualTo("https://links.pos.local");
    }

    @Test
    @DisplayName("Should fall back to base URL when mobile or universal URL is missing")
    void shouldFallBackToBaseUrlWhenSpecificUrlsMissing() {
        FrontendProperties properties = new FrontendProperties();
        properties.setBaseUrl("https://web.pos.local");

        assertThat(properties.resolveBaseUrl(ClientLinkTarget.MOBILE)).isEqualTo("https://web.pos.local");
        assertThat(properties.resolveBaseUrl(ClientLinkTarget.UNIVERSAL)).isEqualTo("https://web.pos.local");
    }
}
