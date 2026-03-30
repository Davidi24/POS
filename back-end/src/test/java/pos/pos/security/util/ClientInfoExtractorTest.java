package pos.pos.security.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import pos.pos.security.config.AppSecurityProperties;

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

        @Test
        @DisplayName("Should throw when trusted proxies list is null")
        void shouldThrow_whenTrustedProxiesNull() {
            assertThrows(IllegalStateException.class,
                    () -> new ClientInfoExtractor(null, 512));
        }

        @Test
        @DisplayName("Should create extractor from AppSecurityProperties")
        void shouldCreateFromProperties() {
            AppSecurityProperties properties = new AppSecurityProperties();
            properties.setTrustedProxies(List.of(TRUSTED_PROXY, "::1"));
            properties.setMaxUserAgentLength(512);

            ClientInfoExtractor extractor = new ClientInfoExtractor(properties);
            MockHttpServletRequest request = trustedProxyRequest();
            request.addHeader("X-Forwarded-For", "10.0.0.1");

            assertEquals("10.0.0.1", extractor.extractIp(request));
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

        @Test
        @DisplayName("X-Forwarded-For must win over X-Real-IP when both are valid")
        void shouldPreferXffOverRealIp_whenBothValid() {
            MockHttpServletRequest request = trustedProxyRequest();
            request.addHeader("X-Forwarded-For", "10.0.0.1");
            request.addHeader("X-Real-IP", "10.0.0.2");

            assertEquals("10.0.0.1", extractor.extractIp(request));
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

        @Test
        @DisplayName("Should ignore X-Real-IP alone when remote address is not trusted")
        void shouldIgnoreRealIp_whenUntrustedRemote() {
            MockHttpServletRequest request = requestFrom(UNTRUSTED_REMOTE);
            request.addHeader("X-Real-IP", "10.0.0.1");

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

        @SuppressWarnings("DataFlowIssue")
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

        @Test
        @DisplayName("Should reject triple colon (:::)")
        void shouldRejectTripleColon() {
            assertFalse(ClientInfoExtractor.isValidIp(":::"));
        }

        @Test
        @DisplayName("Should reject IPv6 with 9 groups")
        void shouldRejectIpv6WithNineGroups() {
            assertFalse(ClientInfoExtractor.isValidIp("1:2:3:4:5:6:7:8:9"));
        }

        @Test
        @DisplayName("Should reject malformed IPv4-mapped IPv6")
        void shouldRejectMalformedIpv4MappedIpv6() {
            assertFalse(ClientInfoExtractor.isValidIp("::ffff:999.999.999.999"));
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

    @Nested
    @DisplayName("Additional coverage")
    class AdditionalTests {

        /*
         * -----------------------------------------
         * User-Agent truncation + boundaries
         * -----------------------------------------
         */

        @Test
        @DisplayName("Should truncate User-Agent when exceeding max length")
        void shouldTruncateUserAgent() {
            ClientInfoExtractor extractor = new ClientInfoExtractor(List.of(TRUSTED_PROXY), 10);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("User-Agent", "123456789012345");

            assertEquals("1234567890", extractor.extractUserAgent(request));
        }

        @Test
        @DisplayName("Should accept User-Agent at minimum boundary (50)")
        void shouldAcceptMinBoundaryUserAgent() {
            ClientInfoExtractor extractor = new ClientInfoExtractor(List.of(TRUSTED_PROXY), 50);

            String ua = "a".repeat(50);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("User-Agent", ua);

            assertEquals(ua, extractor.extractUserAgent(request));
        }

        @Test
        @DisplayName("Should accept User-Agent at maximum boundary (2048)")
        void shouldAcceptMaxBoundaryUserAgent() {
            ClientInfoExtractor extractor = new ClientInfoExtractor(List.of(TRUSTED_PROXY), 2048);

            String ua = "a".repeat(2048);
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("User-Agent", ua);

            assertEquals(ua, extractor.extractUserAgent(request));
        }

        /*
         * -----------------------------------------
         * IPv6 extraction
         * -----------------------------------------
         */

        @Test
        @DisplayName("Should extract IPv6 from X-Forwarded-For")
        void shouldExtractIpv6FromForwarded() {
            MockHttpServletRequest request = trustedProxyRequest();
            request.addHeader("X-Forwarded-For", "unknown, ::1");

            assertEquals("::1", extractor.extractIp(request));
        }

        /*
         * -----------------------------------------
         * X-Forwarded-For edge formatting
         * -----------------------------------------
         */

        @Test
        @DisplayName("Should handle spaces in X-Forwarded-For")
        void shouldHandleSpacesInForwardedHeader() {
            MockHttpServletRequest request = trustedProxyRequest();
            request.addHeader("X-Forwarded-For", " 10.0.0.1 , 10.0.0.2 ");

            assertEquals("10.0.0.2", extractor.extractIp(request));
        }

        @Test
        @DisplayName("Should fallback to remote when all headers invalid")
        void shouldFallbackToRemote_whenAllHeadersInvalid() {
            MockHttpServletRequest request = trustedProxyRequest();
            request.addHeader("X-Forwarded-For", "<bad>");
            request.addHeader("X-Real-IP", "<bad>");

            assertEquals(TRUSTED_PROXY, extractor.extractIp(request));
        }

        @Test
        @DisplayName("Should pick last valid IP among mixed values")
        void shouldPickLastValidIpAmongMixed() {
            MockHttpServletRequest request = trustedProxyRequest();
            request.addHeader("X-Forwarded-For", "bad, 10.0.0.1, also-bad, 10.0.0.2");

            assertEquals("10.0.0.2", extractor.extractIp(request));
        }

        /*
         * -----------------------------------------
         * Logging behavior (observability)
         * -----------------------------------------
         */

        @Test
        @DisplayName("Should not crash when logging invalid headers")
        void shouldNotCrashOnLoggingInvalidHeaders() {
            MockHttpServletRequest request = trustedProxyRequest();
            request.addHeader("X-Forwarded-For", "<script>");
            request.addHeader("X-Real-IP", "<script>");

            assertDoesNotThrow(() -> extractor.extractIp(request));
        }

        @Test
        @DisplayName("Should not crash when User-Agent exceeds limit (logging path)")
        void shouldNotCrashOnUserAgentLogging() {
            ClientInfoExtractor extractor = new ClientInfoExtractor(List.of(TRUSTED_PROXY), 5);

            MockHttpServletRequest request = new MockHttpServletRequest();
            request.addHeader("User-Agent", "123456789");

            assertDoesNotThrow(() -> extractor.extractUserAgent(request));
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
