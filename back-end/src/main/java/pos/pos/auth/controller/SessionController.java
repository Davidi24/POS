package pos.pos.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pos.pos.auth.service.SessionService;
import pos.pos.exception.auth.InvalidTokenException;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.service.JwtService;
import pos.pos.user.dto.UserSessionResponse;

import java.util.List;
import java.util.UUID;

@Tag(name = "Authentication / Sessions")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;
    private final JwtService jwtService;

    @GetMapping("/sessions")
    @Operation(summary = "List my active sessions")
    public ResponseEntity<List<UserSessionResponse>> getMyActiveSessions(
            Authentication authentication,
            HttpServletRequest request) {
        UUID userId = currentUser(authentication).getId();
        UUID tokenId = extractTokenId(request);
        return ResponseEntity.ok(sessionService.getMyActiveSessions(userId, tokenId));
    }

    @GetMapping("/sessions/current")
    @Operation(summary = "Get my current active session")
    public ResponseEntity<UserSessionResponse> getCurrentSession(
            Authentication authentication,
            HttpServletRequest request) {
        UUID userId = currentUser(authentication).getId();
        UUID tokenId = extractTokenId(request);
        return ResponseEntity.ok(sessionService.getCurrentSession(userId, tokenId));
    }

    @DeleteMapping("/sessions/{sessionId}")
    @Operation(summary = "Revoke one of my active sessions")
    public ResponseEntity<Void> revokeSession(
            @PathVariable UUID sessionId,
            Authentication authentication) {
        UUID userId = currentUser(authentication).getId();
        sessionService.revokeSession(sessionId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions/others")
    @Operation(summary = "Revoke all my other active sessions")
    public ResponseEntity<Void> revokeOtherSessions(
            Authentication authentication,
            HttpServletRequest request) {
        UUID userId = currentUser(authentication).getId();
        UUID tokenId = extractTokenId(request);
        sessionService.revokeOtherSessions(userId, tokenId);
        return ResponseEntity.noContent().build();
    }

    private AuthenticatedUser currentUser(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    private UUID extractTokenId(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new InvalidTokenException();
        }

        try {
            return jwtService.extractTokenId(header.substring(7));
        } catch (RuntimeException ex) {
            throw new InvalidTokenException();
        }
    }
}
