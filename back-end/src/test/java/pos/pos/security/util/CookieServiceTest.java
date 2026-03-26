package pos.pos.security.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import pos.pos.security.config.AuthCookieProperties;
import pos.pos.security.config.JwtProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CookieServiceTest {

    private CookieService cookieService;
    private JwtProperties jwtProperties;
    private AuthCookieProperties authCookieProperties;

    @BeforeEach
    void setUp() {
        jwtProperties = new JwtProperties();
        jwtProperties.setRefreshExpiration(Duration.ofDays(30));

        authCookieProperties = new AuthCookieProperties();
        authCookieProperties.setRefreshTokenName("refreshToken");
        authCookieProperties.setSecure(true);
        authCookieProperties.setRefreshTokenPath("/auth/refresh");
        authCookieProperties.setSameSite("Strict");
        authCookieProperties.setDomain("example.com");

        cookieService = new CookieService(jwtProperties, authCookieProperties);
    }

    @Test
    void addRefreshTokenCookie_shouldSetAllAttributes_withDomain() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService.addRefreshTokenCookie(response, "test-token");

        String header = response.getHeader("Set-Cookie");

        assertThat(header).contains("refreshToken=test-token");
        assertThat(header).contains("HttpOnly");
        assertThat(header).contains("Secure");
        assertThat(header).contains("Path=/auth/refresh");
        assertThat(header).contains("SameSite=Strict");
        assertThat(header).contains("Max-Age=");
        assertThat(header).contains("Domain=example.com");
    }

    @Test
    void addRefreshTokenCookie_shouldNotSetDomain_whenBlank() {
        authCookieProperties.setDomain("   ");
        cookieService = new CookieService(jwtProperties, authCookieProperties);

        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService.addRefreshTokenCookie(response, "test-token");

        String header = response.getHeader("Set-Cookie");

        assertThat(header).doesNotContain("Domain=");
    }

    @Test
    void clearRefreshTokenCookie_shouldExpireCookie_withDomain() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService.clearRefreshTokenCookie(response);

        String header = response.getHeader("Set-Cookie");

        assertThat(header).contains("refreshToken=");
        assertThat(header).contains("Max-Age=0");
        assertThat(header).contains("HttpOnly");
        assertThat(header).contains("Secure");
        assertThat(header).contains("Path=/auth/refresh");
        assertThat(header).contains("SameSite=Strict");
        assertThat(header).contains("Domain=example.com");
    }

    @Test
    void clearRefreshTokenCookie_shouldExpireCookie_withoutDomain() {
        authCookieProperties.setDomain(null);
        cookieService = new CookieService(jwtProperties, authCookieProperties);

        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService.clearRefreshTokenCookie(response);

        String header = response.getHeader("Set-Cookie");

        assertThat(header).doesNotContain("Domain=");
        assertThat(header).contains("Max-Age=0");
    }

    @Test
    void getRefreshTokenCookieName_shouldReturnCorrectName() {
        String name = cookieService.getRefreshTokenCookieName();

        assertThat(name).isEqualTo("refreshToken");
    }
}