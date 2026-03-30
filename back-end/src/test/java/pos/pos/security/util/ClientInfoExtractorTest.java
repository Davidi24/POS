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
            new ClientInfoExtractor(List.of(TRUSTED_PROXY, "::1"), 512);

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
                    () -> new ClientInfoExtractor(List.of(), 512));
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
        @DisplayName("Should extract last valid IP from X-Forwarded-For to prevent client spoofing")
        void shouldUseLastForwardedIp() {
            MockHttpServletRequest request = trustedProxyRequest();
            request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");

            assertEquals("10.0.0.2", extractor.extractIp(request));
        }

        @Test
        @DisplayName("Should reject malformed IP in X-Forwarded-For and fall back")
        void shouldRejectMalformedIp() {
            MockHttpServletRequest request = trustedProxyRequest();
            request.addHeader("X-Forwarded-For", "<script>alert(1)</script>, 10.0.0.1");

            assertEquals("10.0.0.1", extractor.extractIp(request));
        }

        @Test
        @DisplayName("Should reject malformed X-Real-IP and fall back to remote address")
        void shouldRejectMalformedRealIp() {
            MockHttpServletRequest request = trustedProxyRequest();
            request.addHeader("X-Real-IP", "not-an-ip");

            assertEquals(TRUSTED_PROXY, extractor.extractIp(request));
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
     * IP Validation
     * =========================================
     */

    @Nested
    @DisplayName("IP validation")
    class IpValidationTests {

        @Test
        @DisplayName("Should accept valid IPv4")
        void shouldAcceptIpv4() {
            assertTrue(ClientInfoExtractor.isValidIp("192.168.1.1"));
        }

        @Test
        @DisplayName("Should accept valid IPv6")
        void shouldAcceptIpv6() {
            assertTrue(ClientInfoExtractor.isValidIp("::1"));
        }

        @Test
        @DisplayName("Should reject hostname")
        void shouldRejectHostname() {
            assertFalse(ClientInfoExtractor.isValidIp("evil.com"));
        }

        @Test
        @DisplayName("Should reject script injection")
        void shouldRejectScriptInjection() {
            assertFalse(ClientInfoExtractor.isValidIp("<script>alert(1)</script>"));
        }

        @Test
        @DisplayName("Should reject out-of-range IPv4 octet")
        void shouldRejectOutOfRangeOctet() {
            assertFalse(ClientInfoExtractor.isValidIp("999.999.999.999"));
        }

        @Test
        @DisplayName("Should reject string exceeding 45 characters")
        void shouldRejectTooLongString() {
            assertFalse(ClientInfoExtractor.isValidIp("1".repeat(46)));
        }

        @Test
        @DisplayName("Should reject null")
        void shouldRejectNull() {
            assertFalse(ClientInfoExtractor.isValidIp(null));
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