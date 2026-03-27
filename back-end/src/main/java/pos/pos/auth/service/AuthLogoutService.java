package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.security.service.JwtService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthLogoutService {

    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";
    private static final String LOGOUT_REASON = "LOGOUT";
    private static final String LOGOUT_ALL_REASON = "LOGOUT_ALL";

    private final UserSessionRepository userSessionRepository;
    private final JwtService jwtService;

    @Transactional
    public void logout(String refreshToken) {
        String normalizedRefreshToken = normalizeRefreshToken(refreshToken);

        validateRefreshToken(normalizedRefreshToken);

        UUID tokenId = jwtService.extractTokenId(normalizedRefreshToken);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        UserSession session = userSessionRepository.findByTokenIdAndRevokedFalse(tokenId)
                .orElseThrow(() -> new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE));

        revokeSession(session, now, LOGOUT_REASON);
    }

    @Transactional
    public void logoutAll(String refreshToken)  {
        String normalizedRefreshToken = normalizeRefreshToken(refreshToken);

        validateRefreshToken(normalizedRefreshToken);

        UUID tokenId = jwtService.extractTokenId(normalizedRefreshToken);
        UUID userId = jwtService.extractUserId(normalizedRefreshToken);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        UserSession currentSession = userSessionRepository.findByTokenIdAndRevokedFalse(tokenId)
                .orElseThrow(() -> new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE));

        if (!currentSession.getUserId().equals(userId)) {
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        userSessionRepository.revokeAllActiveSessionsByUserId(userId, now, LOGOUT_ALL_REASON);
    }

    private String normalizeRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }

        return refreshToken.trim();
    }

    private void validateRefreshToken(String refreshToken) {
        if (!jwtService.isValid(refreshToken) || !jwtService.isRefreshToken(refreshToken)) {
            throw new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
        }
    }

    private void revokeSession(UserSession session, OffsetDateTime now, String reason) {
        session.setRevoked(true);
        session.setRevokedAt(now);
        session.setRevokedReason(reason);
        userSessionRepository.save(session);
    }
}