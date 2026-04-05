package pos.pos.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.auth.dto.ChangePasswordRequest;
import pos.pos.auth.dto.ForgotPasswordRequest;
import pos.pos.auth.dto.ResetPasswordRequest;
import pos.pos.auth.service.ChangePasswordService;
import pos.pos.auth.service.PasswordResetService;
import pos.pos.exception.auth.InvalidTokenException;
import pos.pos.security.service.JwtService;
import pos.pos.user.entity.User;

import java.util.UUID;

@Tag(name = "Authentication / Password")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordResetService passwordResetService;
    private final ChangePasswordService changePasswordService;
    private final JwtService jwtService;

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset email")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using a valid password reset token")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/change-password")
    @Operation(summary = "Change password for the currently authenticated user")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {
        User user = (User) authentication.getPrincipal();
        UUID tokenId = extractTokenId(httpRequest);
        changePasswordService.changePassword(user, tokenId, request);
        return ResponseEntity.noContent().build();
    }

    private UUID extractTokenId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new InvalidTokenException();
        }
        return jwtService.extractTokenId(header.substring(7));
    }
}
