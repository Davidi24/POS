package pos.pos.security.util;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import pos.pos.security.config.AuthCookieProperties;
import pos.pos.security.config.JwtProperties;

@Component
@RequiredArgsConstructor
public class CookieService {

    private final JwtProperties jwtProperties;
    private final AuthCookieProperties authCookieProperties;

    public void addAccessTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie.ResponseCookieBuilder builder = baseCookieBuilder(
                authCookieProperties.getAccessTokenName(),
                token,
                "/"
        ).maxAge(jwtProperties.getAccessExpiration());

        addCookieHeader(response, builder);
    }

    public void addRefreshTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie.ResponseCookieBuilder builder = baseCookieBuilder(
                authCookieProperties.getRefreshTokenName(),
                token,
                authCookieProperties.getRefreshTokenPath()
        ).maxAge(jwtProperties.getRefreshExpiration());

        addCookieHeader(response, builder);
    }

    public void clearAccessTokenCookie(HttpServletResponse response) {
        ResponseCookie.ResponseCookieBuilder builder = baseCookieBuilder(
                authCookieProperties.getAccessTokenName(),
                "",
                "/"
        ).maxAge(0);

        addCookieHeader(response, builder);
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie.ResponseCookieBuilder builder = baseCookieBuilder(
                authCookieProperties.getRefreshTokenName(),
                "",
                authCookieProperties.getRefreshTokenPath()
        ).maxAge(0);

        addCookieHeader(response, builder);
    }

    private ResponseCookie.ResponseCookieBuilder baseCookieBuilder(String name, String value, String path) {
        return ResponseCookie
                .from(name, value)
                .httpOnly(true)
                .secure(authCookieProperties.getSecure())
                .path(path)
                .sameSite(authCookieProperties.getSameSite());
    }

    private void addCookieHeader(HttpServletResponse response, ResponseCookie.ResponseCookieBuilder builder) {
        if (authCookieProperties.getDomain() != null && !authCookieProperties.getDomain().isBlank()) {
            builder.domain(authCookieProperties.getDomain().trim());
        }

        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    public String getRefreshTokenCookieName() {
        return authCookieProperties.getRefreshTokenName();
    }
}
