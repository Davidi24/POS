package pos.pos.security.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientInfoExtractorTest {

    private final ClientInfoExtractor extractor = new ClientInfoExtractor();

    @Test
    void extractIp_shouldUseFirstForwardedIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "10.0.0.1, 10.0.0.2");

        assertEquals("10.0.0.1", extractor.extractIp(request));
    }

    @Test
    void extractIp_shouldFallbackToRemoteAddress() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");

        assertEquals("127.0.0.1", extractor.extractIp(request));
    }

    @Test
    void extractUserAgent_shouldReadHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("User-Agent", "JUnit");

        assertEquals("JUnit", extractor.extractUserAgent(request));
    }
}
