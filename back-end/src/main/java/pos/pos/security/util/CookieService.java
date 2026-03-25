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

    public void addRefreshTokenCookie(HttpServletResponse response, String token) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
                .from(authCookieProperties.getRefreshTokenName(), token)
                .httpOnly(true)
                .secure(authCookieProperties.isSecure())
                .path(authCookieProperties.getRefreshTokenPath())
                .sameSite(authCookieProperties.getSameSite())
                .maxAge(jwtProperties.getRefreshExpiration());

        if (authCookieProperties.getDomain() != null && !authCookieProperties.getDomain().isBlank()) {
            builder.domain(authCookieProperties.getDomain().trim());
        }

        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }

    public void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie
                .from(authCookieProperties.getRefreshTokenName(), "")
                .httpOnly(true)
                .secure(authCookieProperties.isSecure())
                .path(authCookieProperties.getRefreshTokenPath())
                .sameSite(authCookieProperties.getSameSite())
                .maxAge(0);

        if (authCookieProperties.getDomain() != null && !authCookieProperties.getDomain().isBlank()) {
            builder.domain(authCookieProperties.getDomain().trim());
        }

        response.addHeader(HttpHeaders.SET_COOKIE, builder.build().toString());
    }
}