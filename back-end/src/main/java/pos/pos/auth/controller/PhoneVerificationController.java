package pos.pos.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.auth.dto.VerifyPhoneRequest;
import pos.pos.auth.service.PhoneVerificationService;
import pos.pos.security.principal.AuthenticatedUser;

// checked
// tested
@Tag(name = "Authentication / Phone Verification")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class PhoneVerificationController {

    private final PhoneVerificationService phoneVerificationService;

    @PostMapping("/request-phone-verification")
    @Operation(summary = "Send a phone verification code to the authenticated user's current phone")
    public ResponseEntity<Void> requestPhoneVerification(Authentication authentication) {
        phoneVerificationService.requestPhoneVerification(currentUser(authentication).getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-phone")
    @Operation(summary = "Verify the authenticated user's phone number using an SMS code")
    public ResponseEntity<Void> verifyPhone(
            @Valid @RequestBody VerifyPhoneRequest request,
            Authentication authentication
    ) {
        phoneVerificationService.verifyPhone(currentUser(authentication).getId(), request);
        return ResponseEntity.noContent().build();
    }

    private AuthenticatedUser currentUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
