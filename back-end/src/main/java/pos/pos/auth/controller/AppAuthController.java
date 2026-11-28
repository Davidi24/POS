package pos.pos.auth.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
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

// checked
// tested
@Tag(name = "Authentication / Device")
@RestController
@RequestMapping("/auth/device")
@RequiredArgsConstructor
public class AppAuthController {

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
        // The app's own id for this device (the per-device sign-in limit, user 2026-11-17).
        // and its name, for the person's list of signed-in devices (user 2026-11-18).
        AuthenticationResponse authResult = authLoginService.login(request,
                clientInfo.withDevice(httpRequest.getHeader("X-Device-Id"), httpRequest.getHeader("X-Device-Name")));
        return ResponseEntity.ok(authResult);
    }

    /** Sign in with a PIN on one of the restaurant's connected tills (its id and secret from pairing). */
    public record PinLoginRequest(java.util.UUID deviceId,
                                  @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String deviceSecret,
                                  @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 10) String pin) {
    }

    @PostMapping("/pin-login")
    public ResponseEntity<AuthenticationResponse> pinLogin(@Valid @RequestBody PinLoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authLoginService.pinLogin(request.deviceId(), request.deviceSecret(), request.pin(), clientInfoExtractor.extract(httpRequest)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponse> refresh(
            @Valid @RequestBody(required = false) RefreshRequest request,
            HttpServletRequest httpRequest
    ) {
        ClientInfo clientInfo = clientInfoExtractor.extract(httpRequest);
        String refreshToken = extractRefreshTokenFromRequest(request);

        AuthenticationResponse authResult = authRefreshService.refresh(refreshToken, clientInfo);
        return ResponseEntity.ok(authResult);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody(required = false) RefreshRequest request) {
        String refreshToken = extractOptionalRefreshTokenFromRequest(request);
        if (refreshToken == null) {
            return ResponseEntity.noContent().build();
        }

        authLogoutService.logout(refreshToken, request == null ? null : request.getReason());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(@Valid @RequestBody(required = false) RefreshRequest request) {
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
