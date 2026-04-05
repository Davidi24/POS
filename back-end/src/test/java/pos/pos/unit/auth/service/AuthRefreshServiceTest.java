package pos.pos.unit.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.auth.dto.AuthenticationResponse;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.exception.auth.TooManyRequestsException;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.config.JwtProperties;
import pos.pos.security.service.JwtService;
import pos.pos.security.service.RefreshRateLimiter;
import pos.pos.security.service.RefreshTokenSecurityService;
import pos.pos.security.util.ClientInfo;
import pos.pos.auth.service.AuthRefreshService;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.entity.User;
import pos.pos.user.mapper.UserMapper;
import pos.pos.user.repository.UserRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthRefreshService")
class AuthRefreshServiceTest {

    private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";
    private static final String TOO_MANY_REQUESTS_MESSAGE = "Too many refresh attempts. Try again later.";

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RefreshTokenSecurityService refreshTokenSecurityService;

    @Mock
    private RefreshRateLimiter refreshRateLimiter;

    @InjectMocks
    private AuthRefreshService authRefreshService;

    @Nested
    @DisplayName("refresh()")
    class Refresh {

        @Test
        @DisplayName("Should rotate tokens and persist normalized client info on success")
        void shouldRotateTokensAndPersistNormalizedClientInfo_onSuccess() {
            UUID oldTokenId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            List<String> roles = List.of("ADMIN");
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "old-hash", oldTokenId, userId);
            UserSession session = activeSession(userId, oldTokenId, OffsetDateTime.now().plusMinutes(5));
            User user = activeUser(userId, "owner@pos.local");
            UserResponse userResponse = UserResponse.builder()
                    .id(userId)
                    .email("owner@pos.local")
                    .roles(roles)
                    .build();

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenIdAndRevokedFalseForUpdate(oldTokenId)).thenReturn(Optional.of(session));
            when(refreshTokenSecurityService.matchesHash(validatedRefreshToken, "stored-hash")).thenReturn(true);
            when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));
            when(roleRepository.findActiveRoleCodesByUserId(userId)).thenReturn(roles);
            when(jwtProperties.getAccessExpiration()).thenReturn(Duration.ofMinutes(15));
            when(jwtService.generateAccessToken(eq(userId), eq(roles), any(UUID.class))).thenReturn("new-access-token");
            when(jwtService.generateRefreshToken(eq(userId), any(UUID.class))).thenReturn("new-refresh-token");
            when(refreshTokenSecurityService.hash("new-refresh-token")).thenReturn("new-refresh-hash");
            when(userMapper.toUserResponse(user, roles)).thenReturn(userResponse);

            AuthenticationResponse response = authRefreshService.refresh(
                    "token",
                    new ClientInfo(" 127.0.0.1 ", " JUnit/5 ")
            );

            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
            assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
            assertThat(response.getTokenType()).isEqualTo("Bearer");
            assertThat(response.getExpiresIn()).isEqualTo(900L);
            assertThat(response.getUser()).isEqualTo(userResponse);

            ArgumentCaptor<UUID> accessTokenIdCaptor = ArgumentCaptor.forClass(UUID.class);
            ArgumentCaptor<UUID> refreshTokenIdCaptor = ArgumentCaptor.forClass(UUID.class);
            verify(jwtService).generateAccessToken(eq(userId), eq(roles), accessTokenIdCaptor.capture());
            verify(jwtService).generateRefreshToken(eq(userId), refreshTokenIdCaptor.capture());
            assertThat(refreshTokenIdCaptor.getValue()).isEqualTo(accessTokenIdCaptor.getValue());

            ArgumentCaptor<UserSession> sessionCaptor = ArgumentCaptor.forClass(UserSession.class);
            verify(userSessionRepository).save(sessionCaptor.capture());
            UserSession savedSession = sessionCaptor.getValue();
            assertThat(savedSession.getTokenId()).isEqualTo(accessTokenIdCaptor.getValue());
            assertThat(savedSession.getRefreshTokenHash()).isEqualTo("new-refresh-hash");
            assertThat(savedSession.getIpAddress()).isEqualTo("127.0.0.1");
            assertThat(savedSession.getUserAgent()).isEqualTo("JUnit/5");
            assertThat(savedSession.getLastUsedAt()).isNotNull();
            assertThat(savedSession.isRevoked()).isFalse();
            assertThat(savedSession.getRevokedAt()).isNull();
            assertThat(savedSession.getRevokedReason()).isNull();

            InOrder inOrder = inOrder(refreshRateLimiter, refreshTokenSecurityService, userSessionRepository);
            inOrder.verify(refreshRateLimiter).check("127.0.0.1");
            inOrder.verify(refreshTokenSecurityService).validate("token");
            inOrder.verify(refreshRateLimiter).checkByTokenId(oldTokenId);
            inOrder.verify(userSessionRepository).findByTokenIdAndRevokedFalseForUpdate(oldTokenId);

            verify(userSessionRepository).findByTokenIdAndRevokedFalseForUpdate(oldTokenId);
            verify(userRepository).findActiveById(userId);
        }

        @Test
        @DisplayName("Should stop before token validation when the IP limiter blocks the request")
        void shouldStopBeforeTokenValidation_whenIpLimiterBlocksRequest() {
            doThrow(new TooManyRequestsException(TOO_MANY_REQUESTS_MESSAGE))
                    .when(refreshRateLimiter).check("127.0.0.1");

            assertThatThrownBy(() -> authRefreshService.refresh("token", new ClientInfo("127.0.0.1", "JUnit")))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessage(TOO_MANY_REQUESTS_MESSAGE);

            verify(refreshTokenSecurityService, never()).validate(any());
            verify(refreshRateLimiter, never()).checkByTokenId(any(UUID.class));
            verify(userSessionRepository, never()).findByTokenIdAndRevokedFalseForUpdate(any(UUID.class));
        }

        @Test
        @DisplayName("Should stop before session lookup when the token limiter blocks the request")
        void shouldStopBeforeSessionLookup_whenTokenLimiterBlocksRequest() {
            UUID tokenId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "old-hash", tokenId, userId);

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            doThrow(new TooManyRequestsException(TOO_MANY_REQUESTS_MESSAGE))
                    .when(refreshRateLimiter).checkByTokenId(tokenId);

            assertThatThrownBy(() -> authRefreshService.refresh("token", new ClientInfo("127.0.0.1", "JUnit")))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessage(TOO_MANY_REQUESTS_MESSAGE);

            verify(refreshRateLimiter).check("127.0.0.1");
            verify(refreshTokenSecurityService).validate("token");
            verify(userSessionRepository, never()).findByTokenIdAndRevokedFalseForUpdate(any(UUID.class));
        }

        @Test
        @DisplayName("Should throw when the session does not exist")
        void shouldThrow_whenSessionDoesNotExist() {
            UUID tokenId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "old-hash", tokenId, userId);

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenIdAndRevokedFalseForUpdate(tokenId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authRefreshService.refresh("token", new ClientInfo("127.0.0.1", "JUnit")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);

            verify(userRepository, never()).findActiveById(any(UUID.class));
            verify(userSessionRepository, never()).save(any(UserSession.class));
        }

        @Test
        @DisplayName("Should pass null IP to the limiter when client info is missing")
        void shouldPassNullIpToLimiter_whenClientInfoIsMissing() {
            UUID tokenId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "old-hash", tokenId, userId);

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenIdAndRevokedFalseForUpdate(tokenId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authRefreshService.refresh("token", null))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);

            verify(refreshRateLimiter).check(null);
            verify(refreshRateLimiter).checkByTokenId(tokenId);
        }

        @Test
        @DisplayName("Should revoke the session when it is expired")
        void shouldRevokeSession_whenExpired() {
            UUID tokenId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "old-hash", tokenId, userId);
            UserSession session = activeSession(userId, tokenId, OffsetDateTime.now().minusMinutes(1));

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenIdAndRevokedFalseForUpdate(tokenId)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> authRefreshService.refresh("token", new ClientInfo("127.0.0.1", "JUnit")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);

            assertRevokedWithReason(SessionRevocationReason.EXPIRED);
            verify(refreshTokenSecurityService, never()).matchesHash(any(), any());
            verify(userRepository, never()).findActiveById(any(UUID.class));
        }

        @Test
        @DisplayName("Should revoke the session when the token user does not match the session user")
        void shouldRevokeSession_whenTokenUserDoesNotMatchSessionUser() {
            UUID tokenId = UUID.randomUUID();
            UUID tokenUserId = UUID.randomUUID();
            UUID sessionUserId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "old-hash", tokenId, tokenUserId);
            UserSession session = activeSession(sessionUserId, tokenId, OffsetDateTime.now().plusMinutes(5));

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenIdAndRevokedFalseForUpdate(tokenId)).thenReturn(Optional.of(session));

            assertThatThrownBy(() -> authRefreshService.refresh("token", new ClientInfo("127.0.0.1", "JUnit")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);

            assertRevokedWithReason(SessionRevocationReason.TOKEN_USER_MISMATCH);
            verify(refreshTokenSecurityService, never()).matchesHash(any(), any());
            verify(userRepository, never()).findActiveById(any(UUID.class));
        }

        @Test
        @DisplayName("Should revoke the session when the refresh token hash does not match")
        void shouldRevokeSession_whenRefreshTokenHashDoesNotMatch() {
            UUID tokenId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "old-hash", tokenId, userId);
            UserSession session = activeSession(userId, tokenId, OffsetDateTime.now().plusMinutes(5));

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenIdAndRevokedFalseForUpdate(tokenId)).thenReturn(Optional.of(session));
            when(refreshTokenSecurityService.matchesHash(validatedRefreshToken, "stored-hash")).thenReturn(false);

            assertThatThrownBy(() -> authRefreshService.refresh("token", new ClientInfo("127.0.0.1", "JUnit")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);

            assertRevokedWithReason(SessionRevocationReason.REUSE_DETECTED);
            verify(userRepository, never()).findActiveById(any(UUID.class));
        }

        @Test
        @DisplayName("Should revoke the session when the user no longer exists")
        void shouldRevokeSession_whenUserDoesNotExist() {
            UUID tokenId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "old-hash", tokenId, userId);
            UserSession session = activeSession(userId, tokenId, OffsetDateTime.now().plusMinutes(5));

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenIdAndRevokedFalseForUpdate(tokenId)).thenReturn(Optional.of(session));
            when(refreshTokenSecurityService.matchesHash(validatedRefreshToken, "stored-hash")).thenReturn(true);
            when(userRepository.findActiveById(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authRefreshService.refresh("token", new ClientInfo("127.0.0.1", "JUnit")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);

            assertRevokedWithReason(SessionRevocationReason.USER_NOT_ALLOWED);
            verify(jwtService, never()).generateAccessToken(any(UUID.class), any(), any(UUID.class));
        }

        @Test
        @DisplayName("Should revoke the session when the user status is not active")
        void shouldRevokeSession_whenUserStatusIsNotActive() {
            UUID tokenId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "old-hash", tokenId, userId);
            UserSession session = activeSession(userId, tokenId, OffsetDateTime.now().plusMinutes(5));
            User user = activeUser(userId, "owner@pos.local");
            user.setStatus("SUSPENDED");

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenIdAndRevokedFalseForUpdate(tokenId)).thenReturn(Optional.of(session));
            when(refreshTokenSecurityService.matchesHash(validatedRefreshToken, "stored-hash")).thenReturn(true);
            when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authRefreshService.refresh("token", new ClientInfo("127.0.0.1", "JUnit")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);

            assertRevokedWithReason(SessionRevocationReason.USER_NOT_ALLOWED);
            verify(jwtService, never()).generateAccessToken(any(UUID.class), any(), any(UUID.class));
        }

        @Test
        @DisplayName("Should revoke the session when the email is not verified")
        void shouldRevokeSession_whenEmailIsNotVerified() {
            UUID tokenId = UUID.randomUUID();
            UUID userId = UUID.randomUUID();
            RefreshTokenSecurityService.ValidatedRefreshToken validatedRefreshToken =
                    validatedRefreshToken("token", "old-hash", tokenId, userId);
            UserSession session = activeSession(userId, tokenId, OffsetDateTime.now().plusMinutes(5));
            User user = activeUser(userId, "owner@pos.local");
            user.setEmailVerified(false);

            when(refreshTokenSecurityService.validate("token")).thenReturn(validatedRefreshToken);
            when(userSessionRepository.findByTokenIdAndRevokedFalseForUpdate(tokenId)).thenReturn(Optional.of(session));
            when(refreshTokenSecurityService.matchesHash(validatedRefreshToken, "stored-hash")).thenReturn(true);
            when(userRepository.findActiveById(userId)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authRefreshService.refresh("token", new ClientInfo("127.0.0.1", "JUnit")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_REFRESH_TOKEN_MESSAGE);

            assertRevokedWithReason(SessionRevocationReason.USER_NOT_ALLOWED);
            verify(jwtService, never()).generateAccessToken(any(UUID.class), any(), any(UUID.class));
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
                .refreshTokenHash("stored-hash")
                .expiresAt(expiresAt)
                .revoked(false)
                .build();
    }

    private User activeUser(UUID userId, String email) {
        return User.builder()
                .id(userId)
                .email(email)
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .build();
    }
}
