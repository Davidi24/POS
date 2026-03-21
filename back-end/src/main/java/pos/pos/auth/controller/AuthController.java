package pos.pos.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import pos.pos.auth.dto.*;
import pos.pos.auth.service.AuthService;
import pos.pos.exception.auth.InvalidTokenException;
import pos.pos.security.util.ClientInfoExtractor;
import pos.pos.security.util.CookieService;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.entity.User;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ClientInfoExtractor clientInfoExtractor;
    private final CookieService cookieService;

    private static final String CLIENT_WEB = "web";



    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = "X-Client-Type", required = false) String clientType,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        String ipAddress = clientInfoExtractor.extractIp(httpRequest);
        String userAgent = clientInfoExtractor.extractUserAgent(httpRequest);

        LoginResponse loginResponse = authService.login(request, ipAddress, userAgent);

        if (isWeb(clientType)) {
            setRefreshCookie(response, loginResponse.getRefreshToken());
            return ResponseEntity.ok(buildAccessResponse(loginResponse));
        }

        return ResponseEntity.ok(loginResponse);
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(authService.me(user.getId()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @RequestHeader(value = "X-Client-Type", required = false) String clientType,
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken,
            @RequestBody(required = false) RefreshTokenRequest body,
            HttpServletResponse response
    ) {
        String refreshToken = extractRefreshToken(clientType, cookieRefreshToken, body);

        LoginResponse loginResponse = authService.refresh(refreshToken);

        if (isWeb(clientType)) {
            setRefreshCookie(response, loginResponse.getRefreshToken());
            return ResponseEntity.ok(buildAccessResponse(loginResponse));
        }

        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "X-Client-Type", required = false) String clientType,
            @CookieValue(name = "refreshToken", required = false) String cookieRefreshToken,
            @RequestBody(required = false) RefreshTokenRequest body,
            HttpServletResponse response
    ) {
        String refreshToken = tryExtractRefreshToken(clientType, cookieRefreshToken, body);

        if (refreshToken != null) {
            authService.logout(refreshToken);
        }

        if (isWeb(clientType)) {
            cookieService.clearRefreshToken(response);
        }

        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        authService.logoutAll(user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        User user = (User) authentication.getPrincipal();
        authService.changePassword(user.getId(), request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        authService.forgotPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request
    ) {
        authService.verifyEmail(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request
    ) {
        authService.resendVerification(request);
        return ResponseEntity.ok().build();
    }


    private boolean isWeb(String clientType) {
        return CLIENT_WEB.equalsIgnoreCase(clientType);
    }

    private void setRefreshCookie(HttpServletResponse response, String refreshToken) {
        cookieService.addRefreshToken(
                response,
                refreshToken,
                authService.getRefreshTokenMaxAgeSeconds()
        );
    }

    private LoginResponse buildAccessResponse(LoginResponse loginResponse) {
        return LoginResponse.builder()
                .accessToken(loginResponse.getAccessToken())
                .tokenType(loginResponse.getTokenType())
                .expiresIn(loginResponse.getExpiresIn())
                .build();
    }

    private String extractRefreshToken(
            String clientType,
            String cookieRefreshToken,
            RefreshTokenRequest body
    ) {
        if (isWeb(clientType)) {
            if (cookieRefreshToken == null) {
                throw new InvalidTokenException();
            }
            return cookieRefreshToken;
        }

        if (body == null || body.getRefreshToken() == null) {
            throw new InvalidTokenException();
        }

        return body.getRefreshToken();
    }

    private String tryExtractRefreshToken(
            String clientType,
            String cookieRefreshToken,
            RefreshTokenRequest body
    ) {
        if (isWeb(clientType)) {
            return cookieRefreshToken;
        }

        return body != null ? body.getRefreshToken() : null;
    }
}
