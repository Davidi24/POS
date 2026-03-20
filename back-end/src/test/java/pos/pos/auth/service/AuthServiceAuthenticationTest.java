package pos.pos.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import pos.pos.auth.dto.LoginRequest;
import pos.pos.auth.dto.LoginResponse;
import pos.pos.auth.mapper.AuthMapper;
import pos.pos.auth.mapper.UserSessionMapper;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.exception.auth.InvalidTokenException;
import pos.pos.exception.user.UserNotFoundException;
import pos.pos.security.config.JwtProperties;
import pos.pos.security.service.JwtService;
import pos.pos.security.service.PasswordService;
import pos.pos.support.AuthTestDataFactory;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserSession;
import pos.pos.user.mapper.UserMapper;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserSessionRepository;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceAuthenticationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthMapper authMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserSessionMapper userSessionMapper;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    @Spy
    private AuthService authService;

    @BeforeEach
    void setUp() throws Exception {
        setField("mailFrom", "noreply@example.com");
        setField("frontendBaseUrl", "http://localhost:3000");
    }

    @Test
    void login_shouldGenerateTokensPersistSessionAndReturnResponse() {
        LoginRequest request = AuthTestDataFactory.validLoginRequest();
        User user = AuthTestDataFactory.user();
        UserSession session = AuthTestDataFactory.session(user.getId(), UUID.randomUUID());

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordService.matches(request.getPassword(), user.getPasswordHash())).thenReturn(true);
        when(jwtService.generateAccessToken(user.getId())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(eq(user.getId()), any(UUID.class))).thenReturn("refresh-token");
        when(passwordService.hash("refresh-token")).thenReturn("hashed-refresh-token");
        when(userSessionMapper.toSession(eq(user.getId()), any(UUID.class), eq("hashed-refresh-token"), eq("127.0.0.1"), eq("JUnit"))).thenReturn(session);
        when(jwtService.getAccessTokenExpiration()).thenReturn(Duration.ofMinutes(15));

        LoginResponse response = authService.login(request, "127.0.0.1", "JUnit");

        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(Duration.ofMinutes(15).toMillis(), response.getExpiresIn());
        verify(userSessionRepository).save(session);
    }

    @Test
    void login_shouldThrowWhenEmailIsUnknown() {
        LoginRequest request = AuthTestDataFactory.validLoginRequest();
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request, "127.0.0.1", "JUnit"));
    }

    @Test
    void login_shouldThrowWhenPasswordDoesNotMatch() {
        LoginRequest request = AuthTestDataFactory.validLoginRequest();
        User user = AuthTestDataFactory.user();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordService.matches(request.getPassword(), user.getPasswordHash())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.login(request, "127.0.0.1", "JUnit"));
    }

    @Test
    void me_shouldReturnMappedResponse() {
        User user = AuthTestDataFactory.user();
        UserResponse response = AuthTestDataFactory.userResponse(user);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(userMapper.toUserResponse(user)).thenReturn(response);

        UserResponse result = authService.me(user.getId());

        assertEquals(user.getId(), result.getId());
    }

    @Test
    void me_shouldThrowWhenUserMissing() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> authService.me(userId));
    }

    @Test
    void refresh_shouldRotateSessionAndReturnNewTokens() {
        UUID userId = UUID.randomUUID();
        UUID oldTokenId = UUID.randomUUID();
        UserSession oldSession = AuthTestDataFactory.session(userId, oldTokenId);
        oldSession.setExpiresAt(OffsetDateTime.now().plusHours(1));

        when(jwtProperties.getRefreshExpiration()).thenReturn(Duration.ofDays(30));
        when(jwtService.isValid("refresh-token")).thenReturn(true);
        when(jwtService.extractUserId("refresh-token")).thenReturn(userId);
        when(jwtService.extractTokenId("refresh-token")).thenReturn(oldTokenId);
        when(userSessionRepository.findByTokenIdAndRevokedFalse(oldTokenId)).thenReturn(Optional.of(oldSession));
        when(jwtService.generateRefreshToken(eq(userId), any(UUID.class))).thenReturn("new-refresh-token");
        when(passwordService.hash("new-refresh-token")).thenReturn("new-refresh-hash");
        when(jwtService.generateAccessToken(userId)).thenReturn("new-access-token");
        when(jwtService.getAccessTokenExpiration()).thenReturn(Duration.ofMinutes(15));

        LoginResponse response = authService.refresh("refresh-token");

        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());
        assertEquals(Boolean.TRUE, oldSession.getRevoked());

        ArgumentCaptor<UserSession> captor = ArgumentCaptor.forClass(UserSession.class);
        verify(userSessionRepository).save(captor.capture());
        UserSession newSession = captor.getValue();
        assertEquals(userId, newSession.getUserId());
        assertEquals("new-refresh-hash", newSession.getRefreshTokenHash());
    }

    @Test
    void refresh_shouldThrowWhenTokenInvalid() {
        when(jwtService.isValid("bad-token")).thenReturn(false);

        assertThrows(InvalidTokenException.class, () -> authService.refresh("bad-token"));
    }

    @Test
    void refresh_shouldThrowWhenSessionMissing() {
        UUID tokenId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(jwtService.isValid("refresh-token")).thenReturn(true);
        when(jwtService.extractUserId("refresh-token")).thenReturn(userId);
        when(jwtService.extractTokenId("refresh-token")).thenReturn(tokenId);
        when(userSessionRepository.findByTokenIdAndRevokedFalse(tokenId)).thenReturn(Optional.empty());

        assertThrows(InvalidTokenException.class, () -> authService.refresh("refresh-token"));
    }

    @Test
    void logout_shouldIgnoreInvalidToken() {
        when(jwtService.isValid("bad-token")).thenReturn(false);

        authService.logout("bad-token");

        verify(userSessionRepository, never()).findByTokenIdAndRevokedFalse(any());
    }

    @Test
    void logout_shouldRevokeActiveSession() {
        UUID tokenId = UUID.randomUUID();
        UserSession session = AuthTestDataFactory.session(UUID.randomUUID(), tokenId);

        when(jwtService.isValid("refresh-token")).thenReturn(true);
        when(jwtService.extractTokenId("refresh-token")).thenReturn(tokenId);
        when(userSessionRepository.findByTokenIdAndRevokedFalse(tokenId)).thenReturn(Optional.of(session));

        authService.logout("refresh-token");

        assertEquals(Boolean.TRUE, session.getRevoked());
        assertNotNull(session.getLastUsedAt());
        verify(userSessionRepository).save(session);
    }

    @Test
    void logoutAll_shouldRevokeEverySession() {
        UUID userId = UUID.randomUUID();
        List<UserSession> sessions = List.of(
                AuthTestDataFactory.session(userId, UUID.randomUUID()),
                AuthTestDataFactory.session(userId, UUID.randomUUID())
        );

        when(userSessionRepository.findByUserId(userId)).thenReturn(sessions);

        authService.logoutAll(userId);

        sessions.forEach(session -> assertEquals(Boolean.TRUE, session.getRevoked()));
    }

    private void setField(String name, Object value) throws Exception {
        Field field = AuthService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(authService, value);
    }
}
