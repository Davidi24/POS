package pos.pos.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.auth.service.SessionService;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.user.dto.UserSessionResponse;

import java.util.List;
import java.util.UUID;

@Tag(name = "Authentication / Sessions")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class AdminSessionController {

    private final SessionService sessionService;

    @GetMapping("/{userId}/sessions")
    @PreAuthorize("hasAuthority('SESSIONS_MANAGE')")
    @Operation(summary = "List a user's active sessions")
    public ResponseEntity<List<UserSessionResponse>> getUserActiveSessions(
            @PathVariable UUID userId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(sessionService.getUserActiveSessions(currentUser(authentication).getId(), userId));
    }

    @DeleteMapping("/{userId}/sessions")
    @PreAuthorize("hasAuthority('SESSIONS_MANAGE')")
    @Operation(summary = "Revoke all active sessions for a user")
    public ResponseEntity<Void> revokeAllUserSessions(@PathVariable UUID userId, Authentication authentication) {
        sessionService.revokeAllUserSessions(currentUser(authentication).getId(), userId);
        return ResponseEntity.noContent().build();
    }

    private AuthenticatedUser currentUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
