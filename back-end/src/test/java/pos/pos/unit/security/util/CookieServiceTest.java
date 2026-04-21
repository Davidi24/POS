package pos.pos.unit.security.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import pos.pos.security.config.AuthCookieProperties;
import pos.pos.security.config.JwtProperties;
import pos.pos.security.util.CookieService;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CookieService")
class CookieServiceTest {

    @Test
    @DisplayName("Should add refresh token cookie with configured security attributes")
    void shouldAddRefreshTokenCookie() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setRefreshExpiration(Duration.ofDays(30));

        AuthCookieProperties cookieProperties = new AuthCookieProperties();
        cookieProperties.setRefreshTokenName("refreshToken");
        cookieProperties.setRefreshTokenPath("/auth/web");
        cookieProperties.setSameSite("Strict");
        cookieProperties.setSecure(true);
        cookieProperties.setDomain("  example.com  ");

        CookieService cookieService = new CookieService(jwtProperties, cookieProperties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService.addRefreshTokenCookie(response, "refresh-token-value");

        String cookieHeader = response.getHeader("Set-Cookie");
        assertThat(cookieHeader).contains("refreshToken=refresh-token-value");
        assertThat(cookieHeader).contains("Domain=example.com");
        assertThat(cookieHeader).contains("Path=/auth/web");
        assertThat(cookieHeader).contains("Max-Age=2592000");
        assertThat(cookieHeader).contains("HttpOnly");
        assertThat(cookieHeader).contains("Secure");
        assertThat(cookieHeader).contains("SameSite=Strict");
    }

    @Test
    @DisplayName("Should clear refresh token cookie and omit blank domain")
    void shouldClearRefreshTokenCookie() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setRefreshExpiration(Duration.ofDays(30));

        AuthCookieProperties cookieProperties = new AuthCookieProperties();
        cookieProperties.setRefreshTokenName("refreshToken");
        cookieProperties.setRefreshTokenPath("/auth/web");
        cookieProperties.setSameSite("Strict");
        cookieProperties.setSecure(true);
        cookieProperties.setDomain("   ");

        CookieService cookieService = new CookieService(jwtProperties, cookieProperties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        cookieService.clearRefreshTokenCookie(response);

        String cookieHeader = response.getHeader("Set-Cookie");
        assertThat(cookieHeader).contains("refreshToken=");
        assertThat(cookieHeader).contains("Max-Age=0");
        assertThat(cookieHeader).doesNotContain("Domain=");
        assertThat(cookieService.getRefreshTokenCookieName()).isEqualTo("refreshToken");
    }
}
