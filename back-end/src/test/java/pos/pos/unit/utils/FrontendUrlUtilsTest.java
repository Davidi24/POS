package pos.pos.unit.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pos.pos.utils.FrontendUrlUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FrontendUrlUtilsTest {

    @Nested
    @DisplayName("buildTokenUrl()")
    class BuildTokenUrl {

        @Test
        @DisplayName("Should build a frontend token URL with the token query parameter")
        void shouldBuildFrontendTokenUrl() {
            String url = FrontendUrlUtils.buildTokenUrl(
                    "https://web.pos.local",
                    "/reset-password",
                    "abc123"
            );

            assertEquals("https://web.pos.local/reset-password?token=abc123", url);
        }

        @Test
        @DisplayName("Should append path correctly when the base URL already has a trailing slash")
        void shouldAppendPathWhenBaseUrlHasTrailingSlash() {
            String url = FrontendUrlUtils.buildTokenUrl(
                    "https://web.pos.local/",
                    "verify-email",
                    "xyz789"
            );

            assertEquals("https://web.pos.local/verify-email?token=xyz789", url);
        }

        @Test
        @DisplayName("Should encode query-breaking characters in the token query parameter")
        void shouldEncodeQueryBreakingCharactersInToken() {
            String url = FrontendUrlUtils.buildTokenUrl(
                    "https://web.pos.local",
                    "/reset-password",
                    "a b&c="
            );

            assertEquals("https://web.pos.local/reset-password?token=a%20b%26c%3D", url);
        }
    }
}
