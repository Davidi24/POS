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

    @Transactional
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

    private InvalidCredentialsException invalidRefreshToken() {
        return new InvalidCredentialsException(INVALID_REFRESH_TOKEN_MESSAGE);
    }

    private void revokeSession(UserSession session, OffsetDateTime now, SessionRevocationReason reason) {
        session.setRevoked(true);
        session.setRevokedAt(now);
        session.setRevokedReason(reason.name());
        userSessionRepository.save(session);
    }
}
