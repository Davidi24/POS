package pos.pos.security.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClientInfoExtractorTest {

    private static final String TRUSTED_PROXY = "127.0.0.1";
    private static final String UNTRUSTED_REMOTE = "192.168.1.10";

    private final ClientInfoExtractor extractor =
            new ClientInfoExtractor(List.of(TRUSTED_PROXY, "::1"));

    /*
     * =========================================
     * Constructor Validation
     * =========================================
     */

    @Nested
    @DisplayName("Constructor validation")
    class ConstructorTests {

        @Test
        @DisplayName("Should throw when trusted proxies list is empty")
        void shouldThrow_whenTrustedProxiesEmpty() {
            assertThrows(IllegalStateException.class,
                    () -> new ClientInfoExtractor(List.of()));
        }
    }

    /*
     * =========================================
     * Trusted Proxy Behavior
     * =========================================
     */

    @Nested
    @DisplayName("Trusted proxy scenarios")
    class TrustedProxyTests {

        @Test
        @DisplayName("Should extract first valid IP from X-Forwarded-For")
        void shouldUseFirstForwardedIp() {
            MockHttpServletRequest request = trustedProxyRequest();
            request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");

            assertEquals("10.0.0.1", extractor.extractIp(request));
        }

        @Test
        @DisplayName("Should skip unknown and blank entries in X-Forwarded-For")
        void shouldSkipInvalidForwardedEntries() {
            MockHttpServletRequest request = trustedProxyRequest();
            request.addHeader("X-Forwarded-For", "unknown,   , 10.0.0.3");

            assertEquals("10.0.0.3", extractor.extractIp(request));
        }

        @Test
        @DisplayName("Should fallback to X-Real-IP if forwarded header is missing")
        void shouldFallbackToRealIp_whenForwardedMissing() {
            MockHttpServletRequest request = trustedProxyRequest();
            request.addHeader("X-Real-IP", " 10.0.0.4 ");

            assertEquals("10.0.0.4", extractor.extractIp(request));
        }

        @Test
        @DisplayName("Should fallback to X-Real-IP if forwarded header has no valid IP")
        void shouldFallbackToRealIp_whenForwardedInvalid() {
            MockHttpServletRequest request = trustedProxyRequest();
            request.addHeader("X-Forwarded-For", "unknown,   , unknown");
            request.addHeader("X-Real-IP", "10.0.0.5");

            assertEquals("10.0.0.5", extractor.extractIp(request));
        }

        @Test
        @DisplayName("Should fallback to remote address if proxy headers are invalid")
        void shouldFallbackToRemoteAddr_whenHeadersInvalid() {
            MockHttpServletRequest request = trustedProxyRequest();
            request.addHeader("X-Forwarded-For", "unknown");
            request.addHeader("X-Real-IP", "unknown");

            assertEquals(TRUSTED_PROXY, extractor.extractIp(request));
        }

        @Test
        @DisplayName("Should handle case-insensitive 'unknown'")
        void shouldHandleUnknownCaseInsensitive() {
            MockHttpServletRequest request = trustedProxyRequest();
            request.addHeader("X-Forwarded-For", "UNKNOWN, 10.0.0.6");

            assertEquals("10.0.0.6", extractor.extractIp(request));
        }
    }

    /*
     * =========================================
     * Untrusted Proxy Behavior
     * =========================================
     */

    @Nested
    @DisplayName("Untrusted remote scenarios")
    class UntrustedProxyTests {

        @Test
        @DisplayName("Should ignore proxy headers when remote address is not trusted")
        void shouldIgnoreHeaders_whenUntrustedRemote() {
            MockHttpServletRequest request = requestFrom(UNTRUSTED_REMOTE);
            request.addHeader("X-Forwarded-For", "10.0.0.1");
            request.addHeader("X-Real-IP", "10.0.0.2");

            assertEquals(UNTRUSTED_REMOTE, extractor.extractIp(request));
        }
    }

    /*
     * =========================================
     * Edge Cases
     * =========================================
     */

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should return null when no IP source is usable")
        void shouldReturnNull_whenAllSourcesInvalid() {
            MockHttpServletRequest request = requestFrom("   ");
            request.addHeader("X-Forwarded-For", "   ");
            request.addHeader("X-Real-IP", "   ");

            assertNull(extractor.extractIp(request));
        }

        @Test
        @DisplayName("Should return null when remote address is null")
        void shouldReturnNull_whenRemoteAddrNull() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setRemoteAddr(null);

            assertNull(extractor.extractIp(request));
        }
    }

    /*
     * =========================================
     * User-Agent Extraction
     * =========================================
     */

    @Nested
    @DisplayName("User-Agent extraction")
    class UserAgentTests {

        @Test
        @DisplayName("Should trim User-Agent header")
        void shouldTrimUserAgent() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("User-Agent", "  JUnit  ");

            assertEquals("JUnit", extractor.extractUserAgent(request));
        }

        @Test
        @DisplayName("Should return null when User-Agent is missing")
        void shouldReturnNull_whenUserAgentMissing() {
            MockHttpServletRequest request = new MockHttpServletRequest();

            assertNull(extractor.extractUserAgent(request));
        }

        @Test
        @DisplayName("Should return null when User-Agent is blank")
        void shouldReturnNull_whenUserAgentBlank() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("User-Agent", "   ");

            assertNull(extractor.extractUserAgent(request));
        }
    }

    /*
     * =========================================
     * Combined Extraction
     * =========================================
     */

    @Nested
    @DisplayName("Full extraction")
    class ExtractTests {

        @Test
        @DisplayName("Should build ClientInfo from resolved values")
        void shouldBuildClientInfo() {
            MockHttpServletRequest request = trustedProxyRequest();
            request.addHeader("X-Forwarded-For", "unknown, 10.0.0.1");
            request.addHeader("User-Agent", "  JUnit  ");

            ClientInfo clientInfo = extractor.extract(request);

            assertEquals(new ClientInfo("10.0.0.1", "JUnit"), clientInfo);
        }
    }

    /*
     * =========================================
     * Helpers
     * =========================================
     */

    private MockHttpServletRequest trustedProxyRequest() {
        return requestFrom(TRUSTED_PROXY);
    }

    private MockHttpServletRequest requestFrom(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        return request;
    }
}