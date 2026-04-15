package pos.pos.unit.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pos.pos.auth.dto.AuthenticationResponse;
import pos.pos.auth.dto.LoginRequest;
import pos.pos.auth.entity.AuthLoginAttempt;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.enums.LoginFailureReason;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.enums.SessionType;
import pos.pos.auth.mapper.UserSessionMapper;
import pos.pos.auth.repository.AuthLoginAttemptRepository;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.auth.service.AuthLoginService;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.config.JwtProperties;
import pos.pos.security.service.JwtService;
import pos.pos.security.service.PasswordService;
import pos.pos.security.service.RefreshTokenSecurityService;
import pos.pos.security.util.ClientInfo;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.entity.User;
import pos.pos.user.mapper.UserMapper;
import pos.pos.user.repository.UserRepository;

import java.time.Duration;
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
@DisplayName("AuthLoginService")
class AuthLoginServiceTest {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";
    private static final String TOO_MANY_ATTEMPTS_MESSAGE = "Too many login attempts. Try again later.";
    private static final String DUMMY_PASSWORD_HASH =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOePaWxn96p36aH8uY7f9ZC2w5Q5f5e7a";

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private JwtService jwtService;

    @Mock
    private JwtProperties jwtProperties;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private AuthLoginAttemptRepository authLoginAttemptRepository;

    @Mock
    private UserSessionMapper userSessionMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RefreshTokenSecurityService refreshTokenSecurityService;

    @InjectMocks
    private AuthLoginService authLoginService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authLoginService, "maxFailedAttempts", 5);
        ReflectionTestUtils.setField(authLoginService, "lockDurationMinutes", 15L);
        ReflectionTestUtils.setField(authLoginService, "maxAttemptsPerIp", 20);
        ReflectionTestUtils.setField(authLoginService, "maxAttemptsPerAccount", 5);
        ReflectionTestUtils.setField(authLoginService, "windowMinutes", 5L);
        ReflectionTestUtils.setField(authLoginService, "maxActiveSessions", 3);
    }

    /*
     * =========================================
     * login()
     * =========================================
     */

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("Should return tokens and persist normalized security data on success")
        void shouldReturnTokens_onSuccess() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId, "cashier@pos.local");
            List<String> roles = List.of("ADMIN", "CASHIER");
            UserResponse userResponse = UserResponse.builder()
                    .id(userId)
                    .email("cashier@pos.local")
                    .roles(roles)
                    .build();
            UserSession mappedSession = UserSession.builder()
                    .userId(userId)
                    .refreshTokenHash("hashed-refresh")
                    .sessionType(SessionType.PASSWORD)
                    .build();

            when(userRepository.findByEmailAndDeletedAtIsNull("cashier@pos.local")).thenReturn(Optional.of(user));
            when(authLoginAttemptRepository.countByIpAddressAndAttemptedAtAfter(eq("127.0.0.1"), any(OffsetDateTime.class)))
                    .thenReturn(0L);
            when(authLoginAttemptRepository.countByEmailAndAttemptedAtAfterAndSuccessFalse(eq("cashier@pos.local"), any(OffsetDateTime.class)))
                    .thenReturn(0L);
            when(passwordService.matches("Password123!", "stored-hash")).thenReturn(true);
            when(userSessionRepository.countByUserIdAndRevokedFalseAndExpiresAtAfter(eq(userId), any(OffsetDateTime.class)))
                    .thenReturn(1L);
            when(roleRepository.findActiveRoleCodesByUserId(userId)).thenReturn(roles);
            when(jwtProperties.getAccessExpiration()).thenReturn(Duration.ofMinutes(15));
            when(jwtService.generateAccessToken(eq(userId), eq(roles), any(UUID.class))).thenReturn("access-token");
            when(jwtService.generateRefreshToken(eq(userId), any(UUID.class))).thenReturn("refresh-token");
            when(refreshTokenSecurityService.hash("refresh-token")).thenReturn("hashed-refresh");
            when(userSessionMapper.toSession(
                    eq(userId),
                    any(UUID.class),
                    eq(SessionType.PASSWORD),
                    eq("hashed-refresh"),
                    eq(new ClientInfo("127.0.0.1", "JUnit/5"))
            )).thenReturn(mappedSession);
            when(userMapper.toUserResponse(user, roles)).thenReturn(userResponse);

            AuthenticationResponse response = authLoginService.login(
                    loginRequest("  Cashier@POS.local  ", "Password123!"),
                    new ClientInfo(" 127.0.0.1 ", "  JUnit/5  ")
            );

            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(response.getTokenType()).isEqualTo("Bearer");
            assertThat(response.getExpiresIn()).isEqualTo(900L);
            assertThat(response.getUser()).isEqualTo(userResponse);

            verify(userRepository).findByEmailAndDeletedAtIsNull("cashier@pos.local");

            ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(savedUserCaptor.capture());
            User savedUser = savedUserCaptor.getValue();
            assertThat(savedUser.getFailedLoginAttempts()).isZero();
            assertThat(savedUser.getLockedUntil()).isNull();
            assertThat(savedUser.getLastLoginIp()).isEqualTo("127.0.0.1");
            assertThat(savedUser.getLastLoginAt()).isNotNull();

            ArgumentCaptor<UUID> accessTokenIdCaptor = ArgumentCaptor.forClass(UUID.class);
            ArgumentCaptor<UUID> refreshTokenIdCaptor = ArgumentCaptor.forClass(UUID.class);
            verify(jwtService).generateAccessToken(eq(userId), eq(roles), accessTokenIdCaptor.capture());
            verify(jwtService).generateRefreshToken(eq(userId), refreshTokenIdCaptor.capture());
            assertThat(refreshTokenIdCaptor.getValue()).isEqualTo(accessTokenIdCaptor.getValue());

            verify(userSessionMapper).toSession(
                    eq(userId),
                    eq(accessTokenIdCaptor.getValue()),
                    eq(SessionType.PASSWORD),
                    eq("hashed-refresh"),
                    eq(new ClientInfo("127.0.0.1", "JUnit/5"))
            );
            verify(userSessionRepository).save(mappedSession);
            verify(userSessionRepository, never()).revokeOldestSession(any(UUID.class), any(OffsetDateTime.class), any(String.class));

            ArgumentCaptor<AuthLoginAttempt> attemptCaptor = ArgumentCaptor.forClass(AuthLoginAttempt.class);
            verify(authLoginAttemptRepository).save(attemptCaptor.capture());
            AuthLoginAttempt attempt = attemptCaptor.getValue();
            assertThat(attempt.getUserId()).isEqualTo(userId);
            assertThat(attempt.getEmail()).isEqualTo("cashier@pos.local");
            assertThat(attempt.getIpAddress()).isEqualTo("127.0.0.1");
            assertThat(attempt.getUserAgent()).isEqualTo("JUnit/5");
            assertThat(attempt.isSuccess()).isTrue();
            assertThat(attempt.getFailureReason()).isNull();
            assertThat(attempt.getAttemptedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should use dummy hash and record failed attempt when user does not exist")
        void shouldUseDummyHash_whenUserNotFound() {
            ClientInfo clientInfo = new ClientInfo(" 10.0.0.8 ", "  JUnit/5  ");

            when(authLoginAttemptRepository.countByIpAddressAndAttemptedAtAfter(eq("10.0.0.8"), any(OffsetDateTime.class)))
                    .thenReturn(0L);
            when(authLoginAttemptRepository.countByEmailAndAttemptedAtAfterAndSuccessFalse(eq("missing@pos.local"), any(OffsetDateTime.class)))
                    .thenReturn(0L);
            when(userRepository.findByEmailAndDeletedAtIsNull("missing@pos.local")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authLoginService.login(
                    loginRequest("  Missing@POS.local ", "Password123!"),
                    clientInfo
            ))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_CREDENTIALS_MESSAGE);
            //check if it was called
            verify(passwordService).matches("Password123!", DUMMY_PASSWORD_HASH);
            //this function were never called
            verify(userRepository, never()).save(any(User.class));
            verify(userSessionRepository, never()).save(any(UserSession.class));
            verify(roleRepository, never()).findActiveRoleCodesByUserId(any(UUID.class));

            //capture the object pass to repository and confirm if it has these attributes values
            ArgumentCaptor<AuthLoginAttempt> attemptCaptor = ArgumentCaptor.forClass(AuthLoginAttempt.class);
            verify(authLoginAttemptRepository).save(attemptCaptor.capture());
            AuthLoginAttempt attempt = attemptCaptor.getValue();
            assertThat(attempt.getUserId()).isNull();
            assertThat(attempt.getEmail()).isEqualTo("missing@pos.local");
            assertThat(attempt.getIpAddress()).isEqualTo("10.0.0.8");
            assertThat(attempt.getUserAgent()).isEqualTo("JUnit/5");
            assertThat(attempt.isSuccess()).isFalse();
            assertThat(attempt.getFailureReason()).isEqualTo(LoginFailureReason.INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("Should lock account at threshold and record failure when password is wrong")
        void shouldLockAccount_whenPasswordWrong() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId, "cashier@pos.local");
            user.setFailedLoginAttempts(4);

            when(userRepository.findByEmailAndDeletedAtIsNull("cashier@pos.local")).thenReturn(Optional.of(user));
            when(authLoginAttemptRepository.countByIpAddressAndAttemptedAtAfter(eq("127.0.0.1"), any(OffsetDateTime.class)))
                    .thenReturn(0L);
            when(authLoginAttemptRepository.countByEmailAndAttemptedAtAfterAndSuccessFalse(eq("cashier@pos.local"), any(OffsetDateTime.class)))
                    .thenReturn(0L);
            when(passwordService.matches("WrongPassword123!", "stored-hash")).thenReturn(false);

            OffsetDateTime before = OffsetDateTime.now(ZoneOffset.UTC);

            assertThatThrownBy(() -> authLoginService.login(
                    loginRequest("cashier@pos.local", "WrongPassword123!"),
                    new ClientInfo("127.0.0.1", "JUnit")
            ))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_CREDENTIALS_MESSAGE);

            OffsetDateTime after = OffsetDateTime.now(ZoneOffset.UTC);

            ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(savedUserCaptor.capture());
            User savedUser = savedUserCaptor.getValue();
            assertThat(savedUser.getFailedLoginAttempts()).isEqualTo(5);
            assertThat(savedUser.getLockedUntil()).isNotNull();
            assertThat(savedUser.getLockedUntil())
                    .isAfterOrEqualTo(before.plusMinutes(15))
                    .isBeforeOrEqualTo(after.plusMinutes(15));

            ArgumentCaptor<AuthLoginAttempt> attemptCaptor = ArgumentCaptor.forClass(AuthLoginAttempt.class);
            verify(authLoginAttemptRepository).save(attemptCaptor.capture());
            AuthLoginAttempt attempt = attemptCaptor.getValue();
            assertThat(attempt.isSuccess()).isFalse();
            assertThat(attempt.getFailureReason()).isEqualTo(LoginFailureReason.INVALID_CREDENTIALS);

            verify(jwtService, never()).generateAccessToken(any(UUID.class), any(), any(UUID.class));
            verify(userSessionRepository, never()).save(any(UserSession.class));
        }

        @Test
        @DisplayName("Should reject inactive user before password verification")
        void shouldReject_whenUserInactive() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId, "inactive@pos.local");
            user.setActive(false);

            when(userRepository.findByEmailAndDeletedAtIsNull("inactive@pos.local")).thenReturn(Optional.of(user));
            when(authLoginAttemptRepository.countByEmailAndAttemptedAtAfterAndSuccessFalse(eq("inactive@pos.local"), any(OffsetDateTime.class)))
                    .thenReturn(0L);

            assertThatThrownBy(() -> authLoginService.login(
                    loginRequest("inactive@pos.local", "Password123!"),
                    null
            ))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_CREDENTIALS_MESSAGE);

            verify(passwordService, never()).matches(any(), any());
            verify(userRepository, never()).save(any(User.class));

            ArgumentCaptor<AuthLoginAttempt> attemptCaptor = ArgumentCaptor.forClass(AuthLoginAttempt.class);
            verify(authLoginAttemptRepository).save(attemptCaptor.capture());
            assertThat(attemptCaptor.getValue().getFailureReason()).isEqualTo(LoginFailureReason.ACCOUNT_INACTIVE);
        }

        @Test
        @DisplayName("Should reject unverified user before password verification")
        void shouldReject_whenEmailNotVerified() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId, "unverified@pos.local");
            user.setEmailVerified(false);

            when(userRepository.findByEmailAndDeletedAtIsNull("unverified@pos.local")).thenReturn(Optional.of(user));
            when(authLoginAttemptRepository.countByEmailAndAttemptedAtAfterAndSuccessFalse(eq("unverified@pos.local"), any(OffsetDateTime.class)))
                    .thenReturn(0L);

            assertThatThrownBy(() -> authLoginService.login(
                    loginRequest("unverified@pos.local", "Password123!"),
                    null
            ))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_CREDENTIALS_MESSAGE);

            verify(passwordService, never()).matches(any(), any());
            verify(userRepository, never()).save(any(User.class));

            ArgumentCaptor<AuthLoginAttempt> attemptCaptor = ArgumentCaptor.forClass(AuthLoginAttempt.class);
            verify(authLoginAttemptRepository).save(attemptCaptor.capture());
            assertThat(attemptCaptor.getValue().getFailureReason()).isEqualTo(LoginFailureReason.EMAIL_NOT_VERIFIED);
        }

        @Test
        @DisplayName("Should reject locked user before password verification")
        void shouldReject_whenAccountLocked() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId, "locked@pos.local");
            user.setLockedUntil(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10));

            when(userRepository.findByEmailAndDeletedAtIsNull("locked@pos.local")).thenReturn(Optional.of(user));
            when(authLoginAttemptRepository.countByEmailAndAttemptedAtAfterAndSuccessFalse(eq("locked@pos.local"), any(OffsetDateTime.class)))
                    .thenReturn(0L);

            assertThatThrownBy(() -> authLoginService.login(
                    loginRequest("locked@pos.local", "Password123!"),
                    null
            ))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(INVALID_CREDENTIALS_MESSAGE);

            verify(passwordService, never()).matches(any(), any());
            verify(userRepository, never()).save(any(User.class));

            ArgumentCaptor<AuthLoginAttempt> attemptCaptor = ArgumentCaptor.forClass(AuthLoginAttempt.class);
            verify(authLoginAttemptRepository).save(attemptCaptor.capture());
            assertThat(attemptCaptor.getValue().getFailureReason()).isEqualTo(LoginFailureReason.ACCOUNT_LOCKED);
        }

        @Test
        @DisplayName("Should block login before password verification when IP rate limit is reached")
        void shouldBlockLogin_whenIpRateLimitReached() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId, "cashier@pos.local");

            when(userRepository.findByEmailAndDeletedAtIsNull("cashier@pos.local")).thenReturn(Optional.of(user));
            when(authLoginAttemptRepository.countByIpAddressAndAttemptedAtAfter(eq("127.0.0.1"), any(OffsetDateTime.class)))
                    .thenReturn(20L);

            assertThatThrownBy(() -> authLoginService.login(
                    loginRequest("cashier@pos.local", "Password123!"),
                    new ClientInfo("127.0.0.1", "JUnit")
            ))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(TOO_MANY_ATTEMPTS_MESSAGE);

            verify(authLoginAttemptRepository, never()).countByEmailAndAttemptedAtAfterAndSuccessFalse(any(), any());
            verify(passwordService, never()).matches(any(), any());
            verify(userRepository, never()).save(any(User.class));

            ArgumentCaptor<AuthLoginAttempt> attemptCaptor = ArgumentCaptor.forClass(AuthLoginAttempt.class);
            verify(authLoginAttemptRepository).save(attemptCaptor.capture());
            AuthLoginAttempt attempt = attemptCaptor.getValue();
            assertThat(attempt.getUserId()).isEqualTo(userId);
            assertThat(attempt.getFailureReason()).isEqualTo(LoginFailureReason.IP_RATE_LIMITED);
        }

        @Test
        @DisplayName("Should block login before password verification when account rate limit is reached")
        void shouldBlockLogin_whenAccountRateLimitReached() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId, "cashier@pos.local");

            when(userRepository.findByEmailAndDeletedAtIsNull("cashier@pos.local")).thenReturn(Optional.of(user));
            when(authLoginAttemptRepository.countByIpAddressAndAttemptedAtAfter(eq("127.0.0.1"), any(OffsetDateTime.class)))
                    .thenReturn(0L);
            when(authLoginAttemptRepository.countByEmailAndAttemptedAtAfterAndSuccessFalse(eq("cashier@pos.local"), any(OffsetDateTime.class)))
                    .thenReturn(5L);

            assertThatThrownBy(() -> authLoginService.login(
                    loginRequest("cashier@pos.local", "Password123!"),
                    new ClientInfo("127.0.0.1", "JUnit")
            ))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessage(TOO_MANY_ATTEMPTS_MESSAGE);

            verify(passwordService, never()).matches(any(), any());
            verify(userRepository, never()).save(any(User.class));

            ArgumentCaptor<AuthLoginAttempt> attemptCaptor = ArgumentCaptor.forClass(AuthLoginAttempt.class);
            verify(authLoginAttemptRepository).save(attemptCaptor.capture());
            AuthLoginAttempt attempt = attemptCaptor.getValue();
            assertThat(attempt.getUserId()).isEqualTo(userId);
            assertThat(attempt.getFailureReason()).isEqualTo(LoginFailureReason.ACCOUNT_RATE_LIMITED);
        }

        @Test
        @DisplayName("Should revoke oldest session when session limit is reached")
        void shouldRevokeOldestSession_whenSessionLimitReached() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId, "cashier@pos.local");
            List<String> roles = List.of("ADMIN");
            UserSession mappedSession = UserSession.builder()
                    .userId(userId)
                    .refreshTokenHash("hashed-refresh")
                    .sessionType(SessionType.PASSWORD)
                    .build();

            when(userRepository.findByEmailAndDeletedAtIsNull("cashier@pos.local")).thenReturn(Optional.of(user));
            when(authLoginAttemptRepository.countByIpAddressAndAttemptedAtAfter(eq("127.0.0.1"), any(OffsetDateTime.class)))
                    .thenReturn(0L);
            when(authLoginAttemptRepository.countByEmailAndAttemptedAtAfterAndSuccessFalse(eq("cashier@pos.local"), any(OffsetDateTime.class)))
                    .thenReturn(0L);
            when(passwordService.matches("Password123!", "stored-hash")).thenReturn(true);
            when(userSessionRepository.countByUserIdAndRevokedFalseAndExpiresAtAfter(eq(userId), any(OffsetDateTime.class)))
                    .thenReturn(3L);
            when(userSessionRepository.revokeOldestSession(eq(userId), any(OffsetDateTime.class), eq(SessionRevocationReason.SESSION_LIMIT.name())))
                    .thenReturn(1);
            when(roleRepository.findActiveRoleCodesByUserId(userId)).thenReturn(roles);
            when(jwtProperties.getAccessExpiration()).thenReturn(Duration.ofMinutes(15));
            when(jwtService.generateAccessToken(eq(userId), eq(roles), any(UUID.class))).thenReturn("access-token");
            when(jwtService.generateRefreshToken(eq(userId), any(UUID.class))).thenReturn("refresh-token");
            when(refreshTokenSecurityService.hash("refresh-token")).thenReturn("hashed-refresh");
            when(userSessionMapper.toSession(eq(userId), any(UUID.class), eq(SessionType.PASSWORD), eq("hashed-refresh"), any(ClientInfo.class)))
                    .thenReturn(mappedSession);
            when(userMapper.toUserResponse(eq(user), eq(roles))).thenReturn(UserResponse.builder().id(userId).roles(roles).build());

            authLoginService.login(
                    loginRequest("cashier@pos.local", "Password123!"),
                    new ClientInfo("127.0.0.1", "JUnit")
            );

            verify(userSessionRepository).revokeOldestSession(
                    eq(userId),
                    any(OffsetDateTime.class),
                    eq(SessionRevocationReason.SESSION_LIMIT.name())
            );
            verify(userSessionRepository).save(mappedSession);
        }
    }

    /*
     * =========================================
     * Helpers
     * =========================================
     */

    private LoginRequest loginRequest(String email, String password) {
        return LoginRequest.builder()
                .email(email)
                .password(password)
                .build();
    }

    private User activeUser(UUID userId, String email) {
        return User.builder()
                .id(userId)
                .email(email)
                .passwordHash("stored-hash")
                .firstName("Cash")
                .lastName("ier")
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .failedLoginAttempts(0)
                .build();
    }
}
