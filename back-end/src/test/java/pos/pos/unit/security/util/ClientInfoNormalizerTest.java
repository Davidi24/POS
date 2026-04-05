package pos.pos.security.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClientInfoNormalizer")
class ClientInfoNormalizerTest {

    @Nested
    @DisplayName("normalize")
    class NormalizeTests {

        @Test
        @DisplayName("Should return null when client info is null")
        void shouldReturnNull_whenClientInfoIsNull() {
            assertThat(ClientInfoNormalizer.normalize(null)).isNull();
        }

        @Test
        @DisplayName("Should trim IP and user agent")
        void shouldTrimIpAndUserAgent() {
            ClientInfo normalized = ClientInfoNormalizer.normalize(
                    new ClientInfo(" 127.0.0.1 ", " JUnit/5 ")
            );

            assertThat(normalized).isEqualTo(new ClientInfo("127.0.0.1", "JUnit/5"));
        }

        @Test
        @DisplayName("Should convert blank IP and user agent to null")
        void shouldConvertBlankIpAndUserAgentToNull() {
            ClientInfo normalized = ClientInfoNormalizer.normalize(
                    new ClientInfo("   ", "\t")
            );

            assertThat(normalized).isEqualTo(new ClientInfo(null, null));
        }
    }
}
