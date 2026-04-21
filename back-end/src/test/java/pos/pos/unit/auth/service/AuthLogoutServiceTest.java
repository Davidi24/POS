package pos.pos.unit.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.enums.SessionType;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.auth.service.AuthLogoutService;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.security.service.RefreshTokenSecurityService;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthLogoutService")
class AuthLogoutServiceTest {

    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private RefreshTokenSecurityService refreshTokenSecurityService;

    @InjectMocks
    private AuthLogoutService authLogoutService;

    @Nested
    @DisplayName("logout()")
    class LogoutTests {

        @Test
        @DisplayName("Should be idempotent when session is missing after token validation")
        void shouldBeIdempotentWhenSessionIsMissingAfterTokenValidation() {
            UUID tokenId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "hash", tokenId, userId);

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenId(tokenId)).thenReturn(Optional.empty());

            assertThatCode(() -> authLogoutService.logout("token")).doesNotThrowAnyException();

            verify(userSessionRepository).findByTokenId(tokenId);
            verify(userSessionRepository, never()).save(any(UserSession.class));
        }

        @Test
        @DisplayName("Should revoke active session with logout reason")
        void shouldRevokeActiveSessionWithLogoutReason() {
            UUID tokenId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "hash", tokenId, userId);
            UserSession session = activeSession(userId, tokenId, OffsetDateTime.now().plusMinutes(5));

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenId(tokenId)).thenReturn(Optional.of(session));
            when(refreshTokenSecurityService.matchesHash(validatedRefreshToken, "stored-hash")).thenReturn(true);

            authLogoutService.logout("token");

            assertRevokedWithReason(SessionRevocationReason.LOGOUT);
        }

        @Test
        @DisplayName("Should return silently when session is already revoked")
        void shouldReturnSilentlyWhenSessionIsAlreadyRevoked() {
            UUID tokenId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "hash", tokenId, userId);
            UserSession session = activeSession(userId, tokenId, OffsetDateTime.now().plusMinutes(5));
            session.setRevoked(true);

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenId(tokenId)).thenReturn(Optional.of(session));
            when(refreshTokenSecurityService.matchesHash(validatedRefreshToken, "stored-hash")).thenReturn(true);

            assertThatCode(() -> authLogoutService.logout("token")).doesNotThrowAnyException();

            verify(userSessionRepository, never()).save(any(UserSession.class));
        }

        @Test
        @DisplayName("Should revoke expired session with expired reason")
        void shouldRevokeExpiredSessionWithExpiredReason() {
            UUID tokenId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "hash", tokenId, userId);
            UserSession session = activeSession(userId, tokenId, OffsetDateTime.now().minusMinutes(1));

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenId(tokenId)).thenReturn(Optional.of(session));
            when(refreshTokenSecurityService.matchesHash(validatedRefreshToken, "stored-hash")).thenReturn(true);

            authLogoutService.logout("token");

            assertRevokedWithReason(SessionRevocationReason.EXPIRED);
        }

        @Test
        @DisplayName("Should reject when token user does not match session user")
        void shouldRejectWhenTokenUserDoesNotMatchSessionUser() {
            UUID tokenId = UUID.randomUUID();
            UUID tokenUserId = UUID.randomUUID();
            UUID sessionUserId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "hash", tokenId, tokenUserId);
            UserSession session = activeSession(sessionUserId, tokenId, OffsetDateTime.now().plusMinutes(5));

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenId(tokenId)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> authLogoutService.logout("token"))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);

            verify(refreshTokenSecurityService, never()).matchesHash(any(), anyString());
            verify(userSessionRepository, never()).save(any(UserSession.class));
        }

        @Test
        @DisplayName("Should reject when token hash does not match stored hash")
        void shouldRejectWhenTokenHashDoesNotMatchStoredHash() {
            UUID tokenId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "presented-hash", tokenId, userId);
            UserSession session = activeSession(userId, tokenId, OffsetDateTime.now().plusMinutes(5));

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenId(tokenId)).thenReturn(Optional.of(session));
            when(refreshTokenSecurityService.matchesHash(validatedRefreshToken, "stored-hash")).thenReturn(false);

            assertThatThrownBy(() -> authLogoutService.logout("token"))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);

            verify(userSessionRepository, never()).save(any(UserSession.class));
        }
    }

    @Nested
    @DisplayName("logoutAll()")
    class LogoutAllTests {

        @Test
        @DisplayName("Should revoke all active sessions for the current user")
        void shouldRevokeAllActiveSessionsForTheCurrentUser() {
            UUID tokenId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "hash", tokenId, userId);
            UserSession currentSession = activeSession(userId, tokenId, OffsetDateTime.now().plusMinutes(5));

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenIdAndRevokedFalseForUpdate(tokenId)).thenReturn(Optional.of(currentSession));
            when(refreshTokenSecurityService.matchesHash(validatedRefreshToken, "stored-hash")).thenReturn(true);

            authLogoutService.logoutAll("token");

            verify(userSessionRepository).revokeAllActiveSessionsByUserId(
                    eq(userId),
                    any(OffsetDateTime.class),
                    eq(SessionRevocationReason.LOGOUT_ALL.name())
            );
            verify(userSessionRepository, never()).save(any(UserSession.class));
        }

        @Test
        @DisplayName("Should throw when current session does not exist")
        void shouldThrowWhenCurrentSessionDoesNotExist() {
            UUID tokenId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "hash", tokenId, userId);

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenIdAndRevokedFalseForUpdate(tokenId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authLogoutService.logoutAll("token"))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);

            verify(userSessionRepository, never()).revokeAllActiveSessionsByUserId(any(), any(), anyString());
        }

        @Test
        @DisplayName("Should revoke expired current session and then throw")
        void shouldRevokeExpiredCurrentSessionAndThenThrow() {
            UUID tokenId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "hash", tokenId, userId);
            UserSession currentSession = activeSession(userId, tokenId, OffsetDateTime.now().minusMinutes(1));

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenIdAndRevokedFalseForUpdate(tokenId)).thenReturn(Optional.of(currentSession));

            assertThatThrownBy(() -> authLogoutService.logoutAll("token"))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);

            assertRevokedWithReason(SessionRevocationReason.EXPIRED);
            verify(userSessionRepository, never()).revokeAllActiveSessionsByUserId(any(), any(), anyString());
        }

        @Test
        @DisplayName("Should reject when token user does not match current session user")
        void shouldRejectWhenTokenUserDoesNotMatchCurrentSessionUser() {
            UUID tokenId = UUID.randomUUID();
            UUID tokenUserId = UUID.randomUUID();
            UUID sessionUserId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "hash", tokenId, tokenUserId);
            UserSession currentSession = activeSession(sessionUserId, tokenId, OffsetDateTime.now().plusMinutes(5));

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenIdAndRevokedFalseForUpdate(tokenId)).thenReturn(Optional.of(currentSession));

            assertThatThrownBy(() -> authLogoutService.logoutAll("token"))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);

            verify(refreshTokenSecurityService, never()).matchesHash(any(), anyString());
            verify(userSessionRepository, never()).revokeAllActiveSessionsByUserId(any(), any(), anyString());
        }

        @Test
        @DisplayName("Should remain strict when presented token hash does not match stored hash")
        void shouldRemainStrictWhenPresentedTokenHashDoesNotMatchStoredHash() {
            UUID tokenId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "presented-hash", tokenId, userId);
            UserSession currentSession = activeSession(userId, tokenId, OffsetDateTime.now().plusMinutes(5));

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenIdAndRevokedFalseForUpdate(tokenId)).thenReturn(Optional.of(currentSession));
            when(refreshTokenSecurityService.matchesHash(validatedRefreshToken, "stored-hash")).thenReturn(false);

            assertThatThrownBy(() -> authLogoutService.logoutAll("token"))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);

            verify(userSessionRepository, never()).revokeAllActiveSessionsByUserId(any(), any(), anyString());
        }
    }

    private void assertRevokedWithReason(SessionRevocationReason reason) {
        ArgumentCaptor<UserSession> sessionCaptor = ArgumentCaptor.forClass(UserSession.class);
        verify(userSessionRepository).save(sessionCaptor.capture());
        UserSession savedSession = sessionCaptor.getValue();
        assertThat(savedSession.isRevoked()).isTrue();
        assertThat(savedSession.getRevokedAt()).isNotNull();
        assertThat(savedSession.getRevokedReason()).isEqualTo(reason.name());
    }

    private RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken(
            String token,
            String tokenHash,
            UUID tokenId,
            UUID userId
    ) {
        return new RefreshTokenSecurityService.ValidatedRefreshToken(token, tokenHash, tokenId, userId);
    }

    private UserSession activeSession(UUID userId, UUID tokenId, OffsetDateTime expiresAt) {
        return UserSession.builder()
                .userId(userId)
                .tokenId(tokenId)
                .sessionType(SessionType.PASSWORD)
                .refreshTokenHash("stored-hash")
                .expiresAt(expiresAt)
                .revoked(false)
                .build();
    }
}
