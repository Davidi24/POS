package pos.pos.auth.service;

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
import pos.pos.auth.mapper.UserSessionMapper;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.exception.auth.SessionNotFoundException;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.user.dto.UserSessionResponse;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionService")
class SessionServiceTest {

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private UserSessionMapper userSessionMapper;

    @Mock
    private RoleHierarchyService roleHierarchyService;

    @InjectMocks
    private SessionService sessionService;

    @Nested
    @DisplayName("getMyActiveSessions")
    class GetMySessionsTests {

        @Test
        @DisplayName("Should return active sessions with current flag set correctly")
        void shouldReturnActiveSessionsWithCurrentFlag() {
            UUID userId = UUID.randomUUID();
            UUID currentTokenId = UUID.randomUUID();
            UUID currentSessionId = UUID.randomUUID();
            UUID otherSessionId = UUID.randomUUID();

            UserSession currentSession = session(currentSessionId, userId);
            UserSession otherSession = session(otherSessionId, userId);

            UserSessionResponse currentResponse = response(currentSessionId);
            UserSessionResponse otherResponse = response(otherSessionId);

            when(userSessionRepository.findByTokenIdAndRevokedFalse(currentTokenId))
                    .thenReturn(Optional.of(currentSession));
            when(userSessionRepository.findActiveSessionsByUserId(eq(userId), any()))
                    .thenReturn(List.of(currentSession, otherSession));
            when(userSessionMapper.toSessionResponse(currentSession, true)).thenReturn(currentResponse);
            when(userSessionMapper.toSessionResponse(otherSession, false)).thenReturn(otherResponse);

            List<UserSessionResponse> result = sessionService.getMyActiveSessions(userId, currentTokenId);

            assertThat(result).hasSize(2);
            verify(userSessionMapper).toSessionResponse(currentSession, true);
            verify(userSessionMapper).toSessionResponse(otherSession, false);
        }

        @Test
        @DisplayName("Should mark all sessions as non-current when current token has no matching session")
        void shouldMarkAllAsNonCurrentWhenTokenNotFound() {
            UUID userId = UUID.randomUUID();
            UUID currentTokenId = UUID.randomUUID();
            UserSession session = session(UUID.randomUUID(), userId);

            when(userSessionRepository.findByTokenIdAndRevokedFalse(currentTokenId))
                    .thenReturn(Optional.empty());
            when(userSessionRepository.findActiveSessionsByUserId(eq(userId), any()))
                    .thenReturn(List.of(session));
            when(userSessionMapper.toSessionResponse(session, false)).thenReturn(response(session.getId()));

            List<UserSessionResponse> result = sessionService.getMyActiveSessions(userId, currentTokenId);

            assertThat(result).hasSize(1);
            verify(userSessionMapper).toSessionResponse(session, false);
        }
    }

    @Nested
    @DisplayName("getCurrentSession")
    class GetCurrentSessionTests {

        @Test
        @DisplayName("Should return current session details")
        void shouldReturnCurrentSession() {
            UUID userId = UUID.randomUUID();
            UUID tokenId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            UserSession session = session(sessionId, userId);
            UserSessionResponse response = response(sessionId);

            when(userSessionRepository.findByTokenIdAndRevokedFalse(tokenId))
                    .thenReturn(Optional.of(session));
            when(userSessionMapper.toSessionResponse(session, true)).thenReturn(response);

            UserSessionResponse result = sessionService.getCurrentSession(userId, tokenId);

            assertThat(result).isEqualTo(response);
        }

        @Test
        @DisplayName("Should throw SessionNotFoundException when token not found")
        void shouldThrowWhenTokenNotFound() {
            UUID userId = UUID.randomUUID();
            UUID tokenId = UUID.randomUUID();

            when(userSessionRepository.findByTokenIdAndRevokedFalse(tokenId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> sessionService.getCurrentSession(userId, tokenId))
                    .isInstanceOf(SessionNotFoundException.class);
        }

        @Test
        @DisplayName("Should throw SessionNotFoundException when session belongs to different user")
        void shouldThrowWhenSessionBelongsToDifferentUser() {
            UUID userId = UUID.randomUUID();
            UUID tokenId = UUID.randomUUID();
            UserSession session = session(UUID.randomUUID(), UUID.randomUUID());

            when(userSessionRepository.findByTokenIdAndRevokedFalse(tokenId))
                    .thenReturn(Optional.of(session));

            assertThatThrownBy(() -> sessionService.getCurrentSession(userId, tokenId))
                    .isInstanceOf(SessionNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("revokeSession")
    class RevokeSessionTests {

        @Test
        @DisplayName("Should revoke session with SESSION_REVOKED reason")
        void shouldRevokeSession() {
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            UserSession session = session(sessionId, userId);

            when(userSessionRepository.findByIdAndUserIdAndRevokedFalse(sessionId, userId))
                    .thenReturn(Optional.of(session));

            sessionService.revokeSession(sessionId, userId);

            ArgumentCaptor<UserSession> captor = ArgumentCaptor.forClass(UserSession.class);
            verify(userSessionRepository).save(captor.capture());
            assertThat(captor.getValue().isRevoked()).isTrue();
            assertThat(captor.getValue().getRevokedReason()).isEqualTo(SessionRevocationReason.SESSION_REVOKED.name());
            assertThat(captor.getValue().getRevokedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should throw SessionNotFoundException when session not found or not owned")
        void shouldThrowWhenSessionNotFound() {
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();

            when(userSessionRepository.findByIdAndUserIdAndRevokedFalse(sessionId, userId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> sessionService.revokeSession(sessionId, userId))
                    .isInstanceOf(SessionNotFoundException.class);

            verify(userSessionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("revokeOtherSessions")
    class RevokeOtherSessionsTests {

        @Test
        @DisplayName("Should revoke all sessions except current")
        void shouldRevokeAllExceptCurrent() {
            UUID userId = UUID.randomUUID();
            UUID tokenId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            UserSession currentSession = session(sessionId, userId);

            when(userSessionRepository.findByTokenIdAndRevokedFalse(tokenId))
                    .thenReturn(Optional.of(currentSession));

            sessionService.revokeOtherSessions(userId, tokenId);

            verify(userSessionRepository).revokeAllActiveSessionsByUserIdExcept(
                    eq(userId),
                    eq(sessionId),
                    any(),
                    eq(SessionRevocationReason.SESSION_REVOKED.name())
            );
        }

        @Test
        @DisplayName("Should throw SessionNotFoundException when current session not found")
        void shouldThrowWhenCurrentSessionNotFound() {
            UUID userId = UUID.randomUUID();
            UUID tokenId = UUID.randomUUID();

            when(userSessionRepository.findByTokenIdAndRevokedFalse(tokenId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> sessionService.revokeOtherSessions(userId, tokenId))
                    .isInstanceOf(SessionNotFoundException.class);

            verify(userSessionRepository, never()).revokeAllActiveSessionsByUserIdExcept(any(), any(), any(), any());
        }
    }

    @Nested
    @DisplayName("getUserActiveSessions")
    class GetUserSessionsTests {

        @Test
        @DisplayName("Should return active sessions for the given user with current set to false")
        void shouldReturnActiveSessionsForUser() {
            UUID actorUserId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            UserSession session = session(UUID.randomUUID(), userId);
            UserSessionResponse response = response(session.getId());

            when(userSessionRepository.findActiveSessionsByUserId(eq(userId), any()))
                    .thenReturn(List.of(session));
            when(userSessionMapper.toSessionResponse(session, false)).thenReturn(response);

            List<UserSessionResponse> result = sessionService.getUserActiveSessions(actorUserId, userId);

            assertThat(result).hasSize(1);
            verify(userSessionMapper).toSessionResponse(session, false);
        }
    }

    @Nested
    @DisplayName("revokeAllUserSessions")
    class RevokeAllUserSessionsTests {

        @Test
        @DisplayName("Should revoke all active sessions for the given user")
        void shouldRevokeAllSessions() {
            UUID actorUserId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();

            sessionService.revokeAllUserSessions(actorUserId, userId);

            verify(userSessionRepository).revokeAllActiveSessionsByUserId(
                    eq(userId),
                    any(),
                    eq(SessionRevocationReason.SESSION_REVOKED.name())
            );
        }
    }

    private UserSession session(UUID id, UUID userId) {
        return UserSession.builder()
                .id(id)
                .userId(userId)
                .tokenId(UUID.randomUUID())
                .sessionType("PASSWORD")
                .deviceName("Device")
                .refreshTokenHash("hash")
                .ipAddress("127.0.0.1")
                .userAgent("JUnit")
                .lastUsedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusDays(7))
                .revoked(false)
                .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build();
    }

    private UserSessionResponse response(UUID id) {
        return UserSessionResponse.builder()
                .id(id)
                .build();
    }
}
