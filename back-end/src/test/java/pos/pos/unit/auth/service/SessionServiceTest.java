package pos.pos.unit.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.enums.SessionType;
import pos.pos.auth.mapper.UserSessionMapper;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.auth.service.SessionService;
import pos.pos.exception.auth.SessionNotFoundException;
import pos.pos.exception.user.UserManagementNotAllowedException;
import pos.pos.security.principal.AuthenticatedUser;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

            UserSession currentSession = session(currentSessionId, userId, currentTokenId);
            UserSession otherSession = session(UUID.randomUUID(), userId, UUID.randomUUID());

            UserSessionResponse currentResponse = response(currentSessionId);
            UserSessionResponse otherResponse = response(otherSession.getId());

            when(userSessionRepository.findActiveSessionsByUserId(eq(userId), any()))
                    .thenReturn(List.of(currentSession, otherSession));
            when(userSessionMapper.toSessionResponse(currentSession, true)).thenReturn(currentResponse);
            when(userSessionMapper.toSessionResponse(otherSession, false)).thenReturn(otherResponse);

            List<UserSessionResponse> result = sessionService.getMyActiveSessions(userId, currentTokenId);

            assertThat(result).hasSize(2);
            verify(userSessionRepository, never()).findByTokenIdAndRevokedFalse(any());
            verify(userSessionMapper).toSessionResponse(currentSession, true);
            verify(userSessionMapper).toSessionResponse(otherSession, false);
        }

        @Test
        @DisplayName("Should mark all sessions as non-current when current token has no matching session")
        void shouldMarkAllAsNonCurrentWhenTokenNotFound() {
            UUID userId = UUID.randomUUID();
            UUID currentTokenId = UUID.randomUUID();
            UserSession session = session(UUID.randomUUID(), userId, UUID.randomUUID());

            when(userSessionRepository.findActiveSessionsByUserId(eq(userId), any()))
                    .thenReturn(List.of(session));
            when(userSessionMapper.toSessionResponse(session, false)).thenReturn(response(session.getId()));

            List<UserSessionResponse> result = sessionService.getMyActiveSessions(userId, currentTokenId);

            assertThat(result).hasSize(1);
            verify(userSessionRepository, never()).findByTokenIdAndRevokedFalse(any());
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

        @Test
        @DisplayName("Should throw SessionNotFoundException when session is expired")
        void shouldThrowWhenSessionExpired() {
            UUID userId = UUID.randomUUID();
            UUID tokenId = UUID.randomUUID();
            UserSession session = session(UUID.randomUUID(), userId, tokenId).toBuilder()
                    .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                    .build();

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

        @Test
        @DisplayName("Should throw SessionNotFoundException when session is expired")
        void shouldThrowWhenSessionExpired() {
            UUID userId = UUID.randomUUID();
            UUID sessionId = UUID.randomUUID();
            UserSession session = session(sessionId, userId).toBuilder()
                    .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                    .build();

            when(userSessionRepository.findByIdAndUserIdAndRevokedFalse(sessionId, userId))
                    .thenReturn(Optional.of(session));

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

        @Test
        @DisplayName("Should throw SessionNotFoundException when current session belongs to different user")
        void shouldThrowWhenCurrentSessionBelongsToDifferentUser() {
            UUID userId = UUID.randomUUID();
            UUID tokenId = UUID.randomUUID();
            UserSession currentSession = session(UUID.randomUUID(), UUID.randomUUID(), tokenId);

            when(userSessionRepository.findByTokenIdAndRevokedFalse(tokenId))
                    .thenReturn(Optional.of(currentSession));

            assertThatThrownBy(() -> sessionService.revokeOtherSessions(userId, tokenId))
                    .isInstanceOf(SessionNotFoundException.class);

            verify(userSessionRepository, never()).revokeAllActiveSessionsByUserIdExcept(any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should throw SessionNotFoundException when current session is expired")
        void shouldThrowWhenCurrentSessionExpired() {
            UUID userId = UUID.randomUUID();
            UUID tokenId = UUID.randomUUID();
            UserSession currentSession = session(UUID.randomUUID(), userId, tokenId).toBuilder()
                    .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1))
                    .build();

            when(userSessionRepository.findByTokenIdAndRevokedFalse(tokenId))
                    .thenReturn(Optional.of(currentSession));

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
            UUID userId = UUID.randomUUID();
            UserSession session = session(UUID.randomUUID(), userId);
            UserSessionResponse response = response(session.getId());
            Authentication authentication = authentication(UUID.randomUUID());

            when(userSessionRepository.findActiveSessionsByUserId(eq(userId), any()))
                    .thenReturn(List.of(session));
            when(userSessionMapper.toSessionResponse(session, false)).thenReturn(response);

            List<UserSessionResponse> result = sessionService.getUserActiveSessions(authentication, userId);

            assertThat(result).hasSize(1);
            verify(roleHierarchyService).assertCanManageUser(authentication, userId);
            verify(userSessionMapper).toSessionResponse(session, false);
        }

        @Test
        @DisplayName("Should throw when actor is not allowed to manage the target user")
        void shouldThrowWhenActorCannotManageTargetUser() {
            UUID userId = UUID.randomUUID();
            Authentication authentication = authentication(UUID.randomUUID());

            doThrow(new UserManagementNotAllowedException())
                    .when(roleHierarchyService).assertCanManageUser(authentication, userId);

            assertThatThrownBy(() -> sessionService.getUserActiveSessions(authentication, userId))
                    .isInstanceOf(UserManagementNotAllowedException.class);

            verify(userSessionRepository, never()).findActiveSessionsByUserId(any(UUID.class), any(OffsetDateTime.class));
            verify(userSessionMapper, never()).toSessionResponse(any(), anyBoolean());
        }
    }

    @Nested
    @DisplayName("revokeAllUserSessions")
    class RevokeAllUserSessionsTests {

        @Test
        @DisplayName("Should revoke all active sessions for the given user")
        void shouldRevokeAllSessions() {
            UUID userId = UUID.randomUUID();
            Authentication authentication = authentication(UUID.randomUUID());

            sessionService.revokeAllUserSessions(authentication, userId);

            verify(roleHierarchyService).assertCanManageUser(authentication, userId);
            verify(userSessionRepository).revokeAllActiveSessionsByUserId(
                    eq(userId),
                    any(),
                    eq(SessionRevocationReason.SESSION_REVOKED.name())
            );
        }

        @Test
        @DisplayName("Should throw when actor is not allowed to revoke the target user's sessions")
        void shouldThrowWhenActorCannotRevokeTargetUserSessions() {
            UUID userId = UUID.randomUUID();
            Authentication authentication = authentication(UUID.randomUUID());

            doThrow(new UserManagementNotAllowedException())
                    .when(roleHierarchyService).assertCanManageUser(authentication, userId);

            assertThatThrownBy(() -> sessionService.revokeAllUserSessions(authentication, userId))
                    .isInstanceOf(UserManagementNotAllowedException.class);

            verify(userSessionRepository, never()).revokeAllActiveSessionsByUserId(any(), any(), any());
        }
    }

    private UserSession session(UUID id, UUID userId) {
        return session(id, userId, UUID.randomUUID());
    }

    private UserSession session(UUID id, UUID userId, UUID tokenId) {
        return UserSession.builder()
                .id(id)
                .userId(userId)
                .tokenId(tokenId)
                .sessionType(SessionType.PASSWORD)
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

    private Authentication authentication(UUID userId) {
        AuthenticatedUser user = AuthenticatedUser.builder()
                .id(userId)
                .email("admin@pos.local")
                .active(true)
                .build();

        return new UsernamePasswordAuthenticationToken(user, null, List.of());
    }
}
