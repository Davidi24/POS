package pos.pos.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.WebUtils;
import pos.pos.auth.dto.*;
import pos.pos.auth.service.AuthLoginService;
import pos.pos.auth.service.AuthRefreshService;
import pos.pos.security.util.ClientInfo;
import pos.pos.security.util.ClientInfoExtractor;
import pos.pos.security.util.CookieService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String CLIENT_TYPE_HEADER = "X-Client-Type";
    private static final String WEB_CLIENT = "web";

    private final AuthLoginService authLoginService;
    private final ClientInfoExtractor clientInfoExtractor;
    private final CookieService cookieService;
    private final AuthRefreshService authRefreshService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientType
    ) {
        ClientInfo clientInfo = clientInfoExtractor.extract(httpRequest);

        AuthTokensResponse authResult = authLoginService.login(request, clientInfo);

        boolean isWebClient = WEB_CLIENT.equalsIgnoreCase(clientType);

        if (isWebClient) {
            cookieService.addRefreshTokenCookie(httpResponse, authResult.getRefreshToken());
        }

        LoginResponse response = LoginResponse.builder()
                .accessToken(authResult.getAccessToken())
                .tokenType(authResult.getTokenType())
                .expiresIn(authResult.getExpiresIn())
                .user(authResult.getUser())
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshResponse> refresh(
            @Valid @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            @RequestHeader(value = CLIENT_TYPE_HEADER, required = false) String clientType
    ) {
        ClientInfo clientInfo = clientInfoExtractor.extract(httpRequest);
        boolean isWebClient = WEB_CLIENT.equalsIgnoreCase(clientType);

        String refreshToken = isWebClient
                ? extractRefreshTokenFromCookie(httpRequest)
                : extractRefreshTokenFromRequest(request);

        AuthTokensResponse authResult =  authRefreshService.refresh(refreshToken, clientInfo);

        if (isWebClient) {
            cookieService.addRefreshTokenCookie(httpResponse, authResult.getRefreshToken());
        }

        RefreshResponse response = RefreshResponse.builder()
                .accessToken(authResult.getAccessToken())
                .refreshToken(isWebClient ? null : authResult.getRefreshToken())
                .tokenType(authResult.getTokenType())
                .expiresIn(authResult.getExpiresIn())
                .user(authResult.getUser())
                .build();

        return ResponseEntity.ok(response);
    }

    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        var cookie = WebUtils.getCookie(request, cookieService.getRefreshTokenCookieName());

        if (cookie == null || !StringUtils.hasText(cookie.getValue())) {
            throw new IllegalArgumentException("Refresh token is missing");
        }

        return cookie.getValue().trim();
    }

    private String extractRefreshTokenFromRequest(RefreshRequest request) {
        if (request == null || !StringUtils.hasText(request.getRefreshToken())) {
            throw new IllegalArgumentException("Refresh token is required");
        }

        return request.getRefreshToken().trim();
    }
}