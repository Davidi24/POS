package pos.pos.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.auth.dto.AuthenticationResponse;
import pos.pos.auth.dto.LoginRequest;
import pos.pos.auth.dto.RefreshRequest;
import pos.pos.auth.service.AuthLoginService;
import pos.pos.auth.service.AuthLogoutService;
import pos.pos.auth.service.AuthRefreshService;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.security.util.ClientInfo;
import pos.pos.security.util.ClientInfoExtractor;

@RestController
@RequestMapping("/auth/device")
@RequiredArgsConstructor
public class DeviceAuthController {

    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";

    private final AuthLoginService authLoginService;
    private final AuthRefreshService authRefreshService;
    private final AuthLogoutService authLogoutService;
    private final ClientInfoExtractor clientInfoExtractor;

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        ClientInfo clientInfo = clientInfoExtractor.extract(httpRequest);
        AuthenticationResponse authResult = authLoginService.login(request, clientInfo);
        return ResponseEntity.ok(authResult);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponse> refresh(
            @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest httpRequest
    ) {
        ClientInfo clientInfo = clientInfoExtractor.extract(httpRequest);
        String refreshToken = extractRefreshTokenFromRequest(request);

        AuthenticationResponse authResult = authRefreshService.refresh(refreshToken, clientInfo);
        return ResponseEntity.ok(authResult);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshRequest request) {
        String refreshToken = extractOptionalRefreshTokenFromRequest(request);
        if (refreshToken == null) {
            return ResponseEntity.noContent().build();
        }

        authLogoutService.logout(refreshToken);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@RequestBody(required = false) RefreshRequest request) {
        String refreshToken = extractRefreshTokenFromRequest(request);
        authLogoutService.logoutAll(refreshToken);
        return ResponseEntity.noContent().build();
    }

    private String extractRefreshTokenFromRequest(RefreshRequest request) {
        if (request == null || !StringUtils.hasText(request.getRefreshToken())) {
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        return request.getRefreshToken().trim();
    }

    private String extractOptionalRefreshTokenFromRequest(RefreshRequest request) {
        if (request == null || !StringUtils.hasText(request.getRefreshToken())) {
            return null;
        }

        return request.getRefreshToken().trim();
    }
}
