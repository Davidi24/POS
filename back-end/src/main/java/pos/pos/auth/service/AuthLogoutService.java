package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.security.service.RefreshTokenSecurityService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class AuthLogoutService {

    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";

    private final UserSessionRepository userSessionRepository;
    private final RefreshTokenSecurityService refreshTokenSecurityService;

    // Logs out the current session using the refresh token.
    // 1. Validates the refresh token signature
    // 2. Finds the session — if not found, silently does nothing (already gone)
    // 3. Validates the session belongs to the correct user and token hash matches
    // 4. If already revoked — silently does nothing
    // 5. If expired — marks it as expired and returns
    // 6. If active — marks it as revoked with reason LOGOUT
    @Transactional
    public void logout(String refreshToken) {
        RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                refreshTokenSecurityService.validate(refreshToken);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        UserSession session = userSessionRepository.findByTokenId(validatedRefreshToken.tokenId()).orElse(null);
        if (session == null) {
            return;
        }

        validateSession(session, validatedRefreshToken);

        if (session.isRevoked()) {
            return;
        }

        if (session.getExpiresAt() == null || !session.getExpiresAt().isAfter(now)) {
            revokeSession(session, now, SessionRevocationReason.EXPIRED);
            return;
        }

        revokeSession(session, now, SessionRevocationReason.LOGOUT);
    }

    // Logs out all active sessions for the user using the current refresh token.
    // 1. Validates the refresh token signature
    // 2. Finds the current session — throws if not found or already revoked
    // 3. If the current session is expired — revokes it and throws an error (user does not log out cos it's not him trying to log out)
    // 4. Validates the session belongs to the correct user and token hash matches
    // 5. Revokes ALL active sessions for this user, including the current one
    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public void logoutAll(String refreshToken)  {
        RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                refreshTokenSecurityService.validate(refreshToken);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        UserSession currentSession = userSessionRepository.findByTokenIdAndRevokedFalseForUpdate(validatedRefreshToken.tokenId())
                .orElseThrow(this::invalidRefreshToken);

        if (currentSession.getExpiresAt() == null || !currentSession.getExpiresAt().isAfter(now)) {
            revokeSession(currentSession, now, SessionRevocationReason.EXPIRED);
            throw invalidRefreshToken();
        }

        validateSession(currentSession, validatedRefreshToken);

        userSessionRepository.revokeAllActiveSessionsByUserId(
                validatedRefreshToken.userId(),
                now,
                SessionRevocationReason.LOGOUT_ALL.name()
        );
    }

    // Validates that the session belongs to the correct user and that the refresh token hash matches.
    // Throws if either check fails — prevents one user from revoking another user's session.
    private void validateSession(
            UserSession session,
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken
    ) {
        if (!session.getUserId().equals(validatedRefreshToken.userId())) {
            throw invalidRefreshToken();
        }

        if (!refreshTokenSecurityService.matchesHash(validatedRefreshToken, session.getRefreshTokenHash())) {
            throw invalidRefreshToken();
        }
    }

    // Returns a generic invalid refresh token exception — same message for all cases to avoid leaking info
    private InvalidCredentialsException invalidRefreshToken() {
        return new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
    }

    // Marks a session as revoked with a timestamp and reason, then saves it to the DB
    private void revokeSession(UserSession session, OffsetDateTime now, SessionRevocationReason reason) {
        session.setRevoked(true);
        session.setRevokedAt(now);
        session.setRevokedReason(reason.name());
        userSessionRepository.save(session);
    }
}
