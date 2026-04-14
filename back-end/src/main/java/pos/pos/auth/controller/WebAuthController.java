package pos.pos.auth.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;
import pos.pos.auth.dto.AuthenticationResponse;
import pos.pos.auth.dto.LoginRequest;
import pos.pos.auth.dto.WebAuthenticationResponse;
import pos.pos.auth.service.AuthLoginService;
import pos.pos.auth.service.AuthLogoutService;
import pos.pos.auth.service.AuthRefreshService;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.security.util.ClientInfo;
import pos.pos.security.util.ClientInfoExtractor;
import pos.pos.security.util.CookieService;

// checked
// tested

@Tag(name = "Authentication / Web")
@RestController
@RequestMapping("/auth/web")
@RequiredArgsConstructor
public class WebAuthController {

    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";

    private final AuthLoginService authLoginService;
    private final AuthRefreshService authRefreshService;
    private final AuthLogoutService authLogoutService;
    private final ClientInfoExtractor clientInfoExtractor;
    private final CookieService cookieService;

    @PostMapping("/login")
    public ResponseEntity<WebAuthenticationResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        ClientInfo clientInfo = clientInfoExtractor.extract(httpRequest);
        AuthenticationResponse authResult = authLoginService.login(request, clientInfo);

        cookieService.addRefreshTokenCookie(httpResponse, authResult.getRefreshToken());

        WebAuthenticationResponse response = WebAuthenticationResponse.builder()
                .accessToken(authResult.getAccessToken())
                .tokenType(authResult.getTokenType())
                .expiresIn(authResult.getExpiresIn())
                .user(authResult.getUser())
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<WebAuthenticationResponse> refresh(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        ClientInfo clientInfo = clientInfoExtractor.extract(httpRequest);

        try {
            String refreshToken = extractRefreshTokenFromCookie(httpRequest);
            AuthenticationResponse authResult = authRefreshService.refresh(refreshToken, clientInfo);

            cookieService.addRefreshTokenCookie(httpResponse, authResult.getRefreshToken());

            WebAuthenticationResponse response = WebAuthenticationResponse.builder()
                    .accessToken(authResult.getAccessToken())
                    .tokenType(authResult.getTokenType())
                    .expiresIn(authResult.getExpiresIn())
                    .user(authResult.getUser())
                    .build();

            return ResponseEntity.ok(response);
        } catch (InvalidCredentialsException ex) {
            cookieService.clearRefreshTokenCookie(httpResponse);
            throw ex;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        try {
            String refreshToken = extractRefreshTokenFromCookie(httpRequest);
            authLogoutService.logout(refreshToken);
        } catch (InvalidCredentialsException ex) {
            cookieService.clearRefreshTokenCookie(httpResponse);
            return ResponseEntity.noContent().build();
        }

        cookieService.clearRefreshTokenCookie(httpResponse);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        try {
            String refreshToken = extractRefreshTokenFromCookie(httpRequest);
            authLogoutService.logoutAll(refreshToken);
        } catch (InvalidCredentialsException ex) {
            cookieService.clearRefreshTokenCookie(httpResponse);
            throw ex;
        }

        cookieService.clearRefreshTokenCookie(httpResponse);
        return ResponseEntity.noContent().build();
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, cookieService.getRefreshTokenCookieName());

        if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        return cookie.getValue().trim();
    }
}
