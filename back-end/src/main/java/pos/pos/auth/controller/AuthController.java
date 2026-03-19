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

import java.time.Duration;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ClientInfoExtractor clientInfoExtractor;
    private final CookieService cookieService;

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response
    ) {
        String ipAddress = clientInfoExtractor.extractIp(httpRequest);
        String userAgent = clientInfoExtractor.extractUserAgent(httpRequest);

        LoginResponse loginResponse = authService.login(request, ipAddress, userAgent);

        cookieService.addRefreshToken(
                response,
                loginResponse.getRefreshToken(),
                Duration.ofDays(30).getSeconds()
        );

        return ResponseEntity.ok(
                LoginResponse.builder()
                        .accessToken(loginResponse.getAccessToken())
                        .tokenType(loginResponse.getTokenType())
                        .expiresIn(loginResponse.getExpiresIn())
                        .build()
        );
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(authService.me(user.getId()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken == null) {
            throw new InvalidTokenException();
        }

        LoginResponse loginResponse = authService.refresh(refreshToken);

        cookieService.addRefreshToken(
                response,
                loginResponse.getRefreshToken(),
                Duration.ofDays(30).getSeconds()
        );

        return ResponseEntity.ok(
                LoginResponse.builder()
                        .accessToken(loginResponse.getAccessToken())
                        .tokenType(loginResponse.getTokenType())
                        .expiresIn(loginResponse.getExpiresIn())
                        .build()
        );
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }

        cookieService.clearRefreshToken(response);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        authService.logoutAll(user.getId());
        return ResponseEntity.ok().build();
    }





//    @PostMapping("/change-password")
//    public ResponseEntity<Void> changePassword(
//            @RequestHeader("Authorization") String token,
//            @Valid @RequestBody ChangePasswordRequest request
//    ) {
//        authService.changePassword(token, request);
//        return ResponseEntity.ok().build();
//    }
//
//    @PostMapping("/forgot-password")
//    public ResponseEntity<Void> forgotPassword(@RequestBody ForgotPasswordRequest request) {
//        authService.forgotPassword(request);
//        return ResponseEntity.ok().build();
//    }
//
//    @PostMapping("/reset-password")
//    public ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
//        authService.resetPassword(request);
//        return ResponseEntity.ok().build();
//    }
//
//    @PostMapping("/verify-email")
//    public ResponseEntity<Void> verifyEmail(@RequestBody VerifyEmailRequest request) {
////        authService.verifyEmail(request);
//        return ResponseEntity.ok().build();
//    }
//
//    @PostMapping("/resend-verification")
//    public ResponseEntity<Void> resendVerification(@RequestBody ResendVerificationRequest request) {
////        authService.resendVerification(request);
//        return ResponseEntity.ok().build();
//    }
}