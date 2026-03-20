package pos.pos.security.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CookieServiceTest {

    private final CookieService cookieService = new CookieService();

    @Test
    void addRefreshToken_shouldAddSecureHttpOnlyCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService.addRefreshToken(response, "token-value", 3600);

        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(cookie.contains("refreshToken=token-value"));
        assertTrue(cookie.contains("HttpOnly"));
        assertTrue(cookie.contains("Secure"));
        assertTrue(cookie.contains("SameSite=Strict"));
        assertTrue(cookie.contains("Path=/"));
    }

    @Test
    void clearRefreshToken_shouldExpireCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService.clearRefreshToken(response);

        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertTrue(cookie.contains("refreshToken="));
        assertTrue(cookie.contains("Max-Age=0"));
    }
}
