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
import pos.pos.auth.dto.CurrentUserResponse;
import pos.pos.auth.dto.LoginRequest;
import pos.pos.auth.entity.AuthLoginAttempt;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.enums.LoginFailureReason;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.enums.SessionType;
import pos.pos.auth.mapper.CurrentUserMapper;
import pos.pos.auth.mapper.UserSessionMapper;
import pos.pos.auth.repository.AuthLoginAttemptRepository;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.auth.service.AuthLoginService;
import pos.pos.exception.auth.LoginRefusedException;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.PermissionRepository;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.config.JwtProperties;
import pos.pos.security.service.JwtService;
import pos.pos.security.service.PasswordService;
import pos.pos.security.service.RefreshTokenSecurityService;
import pos.pos.security.util.ClientInfo;
import pos.pos.user.entity.User;
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

    private static final String INVALID_CREDENTIALS_MESSAGE = "Wrong username or password.";
    private static final String LOCKED_MESSAGE = "Too many wrong passwords.";
    private static final String DEVICE_PAUSED_MESSAGE = "Too many wrong tries on this device.";
    private static final String NETWORK_PAUSED_MESSAGE = "Many wrong tries on this network.";
    private static final String ACCOUNT_OFF_MESSAGE = "Your account is switched off. Ask your manager.";
    private static final String EMAIL_NOT_CONFIRMED_MESSAGE = "Your email isn't confirmed yet. Use the link in the email we sent you, or ask your manager.";
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
    private PermissionRepository permissionRepository;

    @Mock
    private CurrentUserMapper currentUserMapper;

    @Mock
    private RefreshTokenSecurityService refreshTokenSecurityService;

    @InjectMocks
    private AuthLoginService authLoginService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authLoginService, "maxFailedAttempts", 8);
        ReflectionTestUtils.setField(authLoginService, "maxWrongPerDevice", 50);
        ReflectionTestUtils.setField(authLoginService, "maxWrongPerAddress", 200);
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
            User user = activeUser(userId, "cashier@pos.local", "cashier.one");
            List<Role> activeRoles = List.of(
                    role(UUID.randomUUID(), "ADMIN", 100),
                    role(UUID.randomUUID(), "CASHIER", 10)
            );
            List<String> roleCodes = List.of("ADMIN", "CASHIER");
            List<String> permissionCodes = List.of("USERS_READ", "SESSIONS_MANAGE");
            CurrentUserResponse userResponse = CurrentUserResponse.builder()
                    .id(userId)
                    .email("cashier@pos.local")
                    .username("cashier.one")
                    .isActive(true)
                    .roles(roleCodes)
                    .permissions(permissionCodes)
                    .build();
            UserSession mappedSession = UserSession.builder()
                    .userId(userId)
                    .refreshTokenHash("hashed-refresh")
                    .sessionType(SessionType.PASSWORD)
                    .build();

            when(userRepository.findByEmailAndDeletedAtIsNull("cashier@pos.local")).thenReturn(Optional.of(user));
            when(passwordService.matches("Password123!", "stored-hash")).thenReturn(true);
            when(userSessionRepository.countByUserIdAndRevokedFalseAndExpiresAtAfter(eq(userId), any(OffsetDateTime.class)))
                    .thenReturn(1L);
            when(roleRepository.findActiveRolesByUserId(userId)).thenReturn(activeRoles);
            when(permissionRepository.findCodesByRoleIds(activeRoles.stream().map(Role::getId).toList()))
                    .thenReturn(permissionCodes);
            when(jwtProperties.getAccessExpiration()).thenReturn(Duration.ofMinutes(15));
            when(jwtService.generateAccessToken(eq(userId), eq(roleCodes), any(UUID.class))).thenReturn("access-token");
            when(jwtService.generateRefreshToken(eq(userId), any(UUID.class))).thenReturn("refresh-token");
            when(refreshTokenSecurityService.hash("refresh-token")).thenReturn("hashed-refresh");
            when(userSessionMapper.toSession(
                    eq(userId),
                    any(UUID.class),
                    eq(SessionType.PASSWORD),
                    eq("hashed-refresh"),
                    eq(new ClientInfo("127.0.0.1", "JUnit/5"))
            )).thenReturn(mappedSession);
            when(currentUserMapper.toCurrentUserResponse(user, roleCodes, permissionCodes)).thenReturn(userResponse);

            AuthenticationResponse response = authLoginService.login(
                    loginRequest("  Cashier@POS.local  ", "Password123!"),
                    new ClientInfo(" 127.0.0.1 ", "  JUnit/5  ")
            );

            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(response.getTokenType()).isEqualTo("Bearer");
            assertThat(response.getExpiresIn()).isEqualTo(900L);
            assertThat(response.getUser()).isEqualTo(userResponse);
            assertThat(response.getUser().getPermissions()).containsExactly("USERS_READ", "SESSIONS_MANAGE");

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
            verify(jwtService).generateAccessToken(eq(userId), eq(roleCodes), accessTokenIdCaptor.capture());
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
            assertThat(attempt.getIdentifier()).isEqualTo("cashier@pos.local");
            assertThat(attempt.getIpAddress()).isEqualTo("127.0.0.1");
            assertThat(attempt.getUserAgent()).isEqualTo("JUnit/5");
            assertThat(attempt.isSuccess()).isTrue();
            assertThat(attempt.getFailureReason()).isNull();
            assertThat(attempt.getAttemptedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should find a user by username and record the submitted identifier on a wrong password")
        void shouldFindUserByUsername() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId, "cashier@pos.local", "cashier.one");

            when(userRepository.findByUsernameAndDeletedAtIsNull("cashier.one")).thenReturn(Optional.of(user));
            when(passwordService.matches("WrongPassword123!", "stored-hash")).thenReturn(false);

            assertThatThrownBy(() -> authLoginService.login(
                    loginRequest("  Cashier.One ", "WrongPassword123!"),
                    new ClientInfo(" 10.0.0.8 ", "  JUnit/5  ")
            ))
                    .isInstanceOf(LoginRefusedException.class)
                    .hasMessage(INVALID_CREDENTIALS_MESSAGE)
                    .extracting("reason").isEqualTo(LoginRefusedException.Reason.WRONG_CREDENTIALS);

            verify(userRepository).findByUsernameAndDeletedAtIsNull("cashier.one");

            ArgumentCaptor<AuthLoginAttempt> attemptCaptor = ArgumentCaptor.forClass(AuthLoginAttempt.class);
            verify(authLoginAttemptRepository).save(attemptCaptor.capture());
            assertThat(attemptCaptor.getValue().getIdentifier()).isEqualTo("cashier.one");
            assertThat(attemptCaptor.getValue().getFailureReason()).isEqualTo(LoginFailureReason.INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("Should use dummy hash and record failed attempt when user does not exist")
        void shouldUseDummyHashWhenUserNotFound() {
            ClientInfo clientInfo = new ClientInfo(" 10.0.0.8 ", "  JUnit/5  ");

            when(userRepository.findByEmailAndDeletedAtIsNull("missing@pos.local")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authLoginService.login(
                    loginRequest("  Missing@POS.local ", "Password123!"),
                    clientInfo
            ))
                    .isInstanceOf(LoginRefusedException.class)
                    .hasMessage(INVALID_CREDENTIALS_MESSAGE)
                    .extracting("reason").isEqualTo(LoginRefusedException.Reason.WRONG_CREDENTIALS);
            verify(passwordService).matches("Password123!", DUMMY_PASSWORD_HASH);
            verify(userRepository, never()).save(any(User.class));
            verify(userSessionRepository, never()).save(any(UserSession.class));
            verify(roleRepository, never()).findActiveRolesByUserId(any(UUID.class));
            verify(permissionRepository, never()).findCodesByRoleIds(any());

            ArgumentCaptor<AuthLoginAttempt> attemptCaptor = ArgumentCaptor.forClass(AuthLoginAttempt.class);
            verify(authLoginAttemptRepository).save(attemptCaptor.capture());
            AuthLoginAttempt attempt = attemptCaptor.getValue();
            assertThat(attempt.getUserId()).isNull();
            assertThat(attempt.getIdentifier()).isEqualTo("missing@pos.local");
            assertThat(attempt.getIpAddress()).isEqualTo("10.0.0.8");
            assertThat(attempt.getUserAgent()).isEqualTo("JUnit/5");
            assertThat(attempt.isSuccess()).isFalse();
            assertThat(attempt.getFailureReason()).isEqualTo(LoginFailureReason.INVALID_CREDENTIALS);
        }

        @Test
        @DisplayName("Should lock the username when the 8th wrong password within 15 minutes is typed, and say so at once")
        void shouldLockAccount_whenPasswordWrong() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId, "cashier@pos.local", "cashier.one");
            user.setFailedLoginAttempts(7);
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            when(userRepository.findByEmailAndDeletedAtIsNull("cashier@pos.local")).thenReturn(Optional.of(user));
            // Seven wrong passwords before this try, then eight once this one is saved.
            when(authLoginAttemptRepository.wrongPasswordTimesForUser(eq(userId), any(OffsetDateTime.class)))
                    .thenReturn(minutesAgo(now, 7, 6, 5, 4, 3, 2, 1))
                    .thenReturn(minutesAgo(now, 7, 6, 5, 4, 3, 2, 1, 0));
            when(passwordService.matches("WrongPassword123!", "stored-hash")).thenReturn(false);

            assertThatThrownBy(() -> authLoginService.login(
                    loginRequest("cashier@pos.local", "WrongPassword123!"),
                    new ClientInfo("127.0.0.1", "JUnit")
            ))
                    .isInstanceOf(LoginRefusedException.class)
                    .hasMessage(LOCKED_MESSAGE)
                    .satisfies(e -> {
                        LoginRefusedException refused = (LoginRefusedException) e;
                        assertThat(refused.getReason()).isEqualTo(LoginRefusedException.Reason.ACCOUNT_LOCKED);
                        assertThat(refused.getLockedUntil()).isEqualTo(now.plusMinutes(15));
                    });

            ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(savedUserCaptor.capture());
            assertThat(savedUserCaptor.getValue().getFailedLoginAttempts()).isEqualTo(8);
            assertThat(savedUserCaptor.getValue().getLockedUntil()).isEqualTo(now.plusMinutes(15));

            ArgumentCaptor<AuthLoginAttempt> attemptCaptor = ArgumentCaptor.forClass(AuthLoginAttempt.class);
            verify(authLoginAttemptRepository).save(attemptCaptor.capture());
            assertThat(attemptCaptor.getValue().getFailureReason()).isEqualTo(LoginFailureReason.INVALID_CREDENTIALS);

            verify(jwtService, never()).generateAccessToken(any(UUID.class), any(), any(UUID.class));
            verify(userSessionRepository, never()).save(any(UserSession.class));
        }

        @Test
        @DisplayName("Should lock a username nobody has exactly like a real one, so a lock tells nothing")
        void shouldLockUnknownUsernameLikeARealOne() {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            when(userRepository.findByUsernameAndDeletedAtIsNull("nobody")).thenReturn(Optional.empty());
            when(authLoginAttemptRepository.wrongPasswordTimesForUnknown(eq("nobody"), any(OffsetDateTime.class)))
                    .thenReturn(minutesAgo(now, 7, 6, 5, 4, 3, 2, 1))
                    .thenReturn(minutesAgo(now, 7, 6, 5, 4, 3, 2, 1, 0));

            assertThatThrownBy(() -> authLoginService.login(loginRequest("nobody", "Password123!"), new ClientInfo("127.0.0.1", "JUnit")))
                    .isInstanceOf(LoginRefusedException.class)
                    .hasMessage(LOCKED_MESSAGE)
                    .extracting("reason").isEqualTo(LoginRefusedException.Reason.ACCOUNT_LOCKED);
        }

        @Test
        @DisplayName("Should tell a switched-off account only after the right password")
        void shouldReject_whenUserInactive() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId, "inactive@pos.local", "inactive.user");
            user.setActive(false);

            when(userRepository.findByEmailAndDeletedAtIsNull("inactive@pos.local")).thenReturn(Optional.of(user));
            when(passwordService.matches("Password123!", "stored-hash")).thenReturn(true);

            assertThatThrownBy(() -> authLoginService.login(
                    loginRequest("inactive@pos.local", "Password123!"),
                    null
            ))
                    .isInstanceOf(LoginRefusedException.class)
                    .hasMessage(ACCOUNT_OFF_MESSAGE)
                    .extracting("reason").isEqualTo(LoginRefusedException.Reason.ACCOUNT_OFF);

            verify(userRepository, never()).save(any(User.class));

            ArgumentCaptor<AuthLoginAttempt> attemptCaptor = ArgumentCaptor.forClass(AuthLoginAttempt.class);
            verify(authLoginAttemptRepository).save(attemptCaptor.capture());
            assertThat(attemptCaptor.getValue().getFailureReason()).isEqualTo(LoginFailureReason.ACCOUNT_INACTIVE);
        }

        @Test
        @DisplayName("Should answer a switched-off account's wrong password like any wrong password")
        void shouldAnswerWrongPasswordForInactiveUserLikeAnyOther() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId, "inactive@pos.local", "inactive.user");
            user.setActive(false);

            when(userRepository.findByEmailAndDeletedAtIsNull("inactive@pos.local")).thenReturn(Optional.of(user));
            when(passwordService.matches("WrongPassword123!", "stored-hash")).thenReturn(false);

            assertThatThrownBy(() -> authLoginService.login(loginRequest("inactive@pos.local", "WrongPassword123!"), null))
                    .isInstanceOf(LoginRefusedException.class)
                    .hasMessage(INVALID_CREDENTIALS_MESSAGE)
                    .extracting("reason").isEqualTo(LoginRefusedException.Reason.WRONG_CREDENTIALS);
        }

        @Test
        @DisplayName("Should tell an email that isn't confirmed only after the right password")
        void shouldReject_whenEmailNotVerified() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId, "unverified@pos.local", "unverified.user");
            user.setEmailVerified(false);

            when(userRepository.findByEmailAndDeletedAtIsNull("unverified@pos.local")).thenReturn(Optional.of(user));
            when(passwordService.matches("Password123!", "stored-hash")).thenReturn(true);

            assertThatThrownBy(() -> authLoginService.login(
                    loginRequest("unverified@pos.local", "Password123!"),
                    null
            ))
                    .isInstanceOf(LoginRefusedException.class)
                    .hasMessage(EMAIL_NOT_CONFIRMED_MESSAGE)
                    .extracting("reason").isEqualTo(LoginRefusedException.Reason.EMAIL_NOT_CONFIRMED);

            verify(userRepository, never()).save(any(User.class));

            ArgumentCaptor<AuthLoginAttempt> attemptCaptor = ArgumentCaptor.forClass(AuthLoginAttempt.class);
            verify(authLoginAttemptRepository).save(attemptCaptor.capture());
            assertThat(attemptCaptor.getValue().getFailureReason()).isEqualTo(LoginFailureReason.EMAIL_NOT_VERIFIED);
        }

        @Test
        @DisplayName("Should refuse a locked username before checking the password, with until when")
        void shouldReject_whenAccountLocked() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId, "locked@pos.local", "locked.user");
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            when(userRepository.findByEmailAndDeletedAtIsNull("locked@pos.local")).thenReturn(Optional.of(user));
            when(authLoginAttemptRepository.wrongPasswordTimesForUser(eq(userId), any(OffsetDateTime.class)))
                    .thenReturn(minutesAgo(now, 9, 8, 7, 6, 5, 4, 3, 2));

            assertThatThrownBy(() -> authLoginService.login(
                    loginRequest("locked@pos.local", "Password123!"),
                    null
            ))
                    .isInstanceOf(LoginRefusedException.class)
                    .hasMessage(LOCKED_MESSAGE)
                    .satisfies(e -> assertThat(((LoginRefusedException) e).getLockedUntil()).isEqualTo(now.minusMinutes(2).plusMinutes(15)));

            verify(passwordService, never()).matches(any(), any());
            verify(userRepository, never()).save(any(User.class));

            ArgumentCaptor<AuthLoginAttempt> attemptCaptor = ArgumentCaptor.forClass(AuthLoginAttempt.class);
            verify(authLoginAttemptRepository).save(attemptCaptor.capture());
            assertThat(attemptCaptor.getValue().getFailureReason()).isEqualTo(LoginFailureReason.ACCOUNT_LOCKED);
        }

        @Test
        @DisplayName("Should pause sign-in from a network address after 200 wrong passwords within 15 minutes")
        void shouldBlockLogin_whenIpRateLimitReached() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId, "cashier@pos.local", "cashier.one");
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            when(userRepository.findByEmailAndDeletedAtIsNull("cashier@pos.local")).thenReturn(Optional.of(user));
            when(authLoginAttemptRepository.wrongPasswordTimesForAddress(eq("127.0.0.1"), any(OffsetDateTime.class)))
                    .thenReturn(java.util.Collections.nCopies(200, now.minusMinutes(1)));

            assertThatThrownBy(() -> authLoginService.login(
                    loginRequest("cashier@pos.local", "Password123!"),
                    new ClientInfo("127.0.0.1", "JUnit")
            ))
                    .isInstanceOf(LoginRefusedException.class)
                    .hasMessage(NETWORK_PAUSED_MESSAGE)
                    .extracting("reason").isEqualTo(LoginRefusedException.Reason.NETWORK_PAUSED);

            verify(passwordService, never()).matches(any(), any());
            verify(userRepository, never()).save(any(User.class));

            ArgumentCaptor<AuthLoginAttempt> attemptCaptor = ArgumentCaptor.forClass(AuthLoginAttempt.class);
            verify(authLoginAttemptRepository).save(attemptCaptor.capture());
            assertThat(attemptCaptor.getValue().getUserId()).isEqualTo(userId);
            assertThat(attemptCaptor.getValue().getFailureReason()).isEqualTo(LoginFailureReason.IP_RATE_LIMITED);
        }

        @Test
        @DisplayName("Should pause sign-in on one device after 50 wrong passwords within 15 minutes")
        void shouldBlockLogin_whenDeviceLimitReached() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId, "cashier@pos.local", "cashier.one");
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            when(userRepository.findByEmailAndDeletedAtIsNull("cashier@pos.local")).thenReturn(Optional.of(user));
            when(authLoginAttemptRepository.wrongPasswordTimesForDevice(eq("till-1"), any(OffsetDateTime.class)))
                    .thenReturn(java.util.Collections.nCopies(50, now.minusMinutes(1)));

            assertThatThrownBy(() -> authLoginService.login(
                    loginRequest("cashier@pos.local", "Password123!"),
                    new ClientInfo("127.0.0.1", "JUnit", "till-1")
            ))
                    .isInstanceOf(LoginRefusedException.class)
                    .hasMessage(DEVICE_PAUSED_MESSAGE)
                    .extracting("reason").isEqualTo(LoginRefusedException.Reason.DEVICE_PAUSED);

            verify(passwordService, never()).matches(any(), any());

            ArgumentCaptor<AuthLoginAttempt> attemptCaptor = ArgumentCaptor.forClass(AuthLoginAttempt.class);
            verify(authLoginAttemptRepository).save(attemptCaptor.capture());
            assertThat(attemptCaptor.getValue().getDeviceId()).isEqualTo("till-1");
            assertThat(attemptCaptor.getValue().getFailureReason()).isEqualTo(LoginFailureReason.DEVICE_RATE_LIMITED);
        }

        @Test
        @DisplayName("Should revoke oldest session when session limit is reached")
        void shouldRevokeOldestSession_whenSessionLimitReached() {
            UUID userId = UUID.randomUUID();
            User user = activeUser(userId, "cashier@pos.local", "cashier.one");
            List<Role> activeRoles = List.of(role(UUID.randomUUID(), "ADMIN", 100));
            List<String> roleCodes = List.of("ADMIN");
            List<String> permissionCodes = List.of("USERS_READ");
            UserSession mappedSession = UserSession.builder()
                    .userId(userId)
                    .refreshTokenHash("hashed-refresh")
                    .sessionType(SessionType.PASSWORD)
                    .build();

            when(userRepository.findByEmailAndDeletedAtIsNull("cashier@pos.local")).thenReturn(Optional.of(user));
            when(passwordService.matches("Password123!", "stored-hash")).thenReturn(true);
            when(userSessionRepository.countByUserIdAndRevokedFalseAndExpiresAtAfter(eq(userId), any(OffsetDateTime.class)))
                    .thenReturn(3L);
            when(userSessionRepository.revokeOldestSession(eq(userId), any(OffsetDateTime.class), eq(SessionRevocationReason.SESSION_LIMIT.name())))
                    .thenReturn(1);
            when(roleRepository.findActiveRolesByUserId(userId)).thenReturn(activeRoles);
            when(permissionRepository.findCodesByRoleIds(activeRoles.stream().map(Role::getId).toList()))
                    .thenReturn(permissionCodes);
            when(jwtProperties.getAccessExpiration()).thenReturn(Duration.ofMinutes(15));
            when(jwtService.generateAccessToken(eq(userId), eq(roleCodes), any(UUID.class))).thenReturn("access-token");
            when(jwtService.generateRefreshToken(eq(userId), any(UUID.class))).thenReturn("refresh-token");
            when(refreshTokenSecurityService.hash("refresh-token")).thenReturn("hashed-refresh");
            when(userSessionMapper.toSession(eq(userId), any(UUID.class), eq(SessionType.PASSWORD), eq("hashed-refresh"), any(ClientInfo.class)))
                    .thenReturn(mappedSession);
            when(currentUserMapper.toCurrentUserResponse(eq(user), eq(roleCodes), eq(permissionCodes)))
                    .thenReturn(CurrentUserResponse.builder().id(userId).roles(roleCodes).permissions(permissionCodes).build());

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

    // Wrong-password times this many minutes before [now], oldest first, as the repository answers.
    private static List<OffsetDateTime> minutesAgo(OffsetDateTime now, int... minutes) {
        return java.util.Arrays.stream(minutes).mapToObj(now::minusMinutes).toList();
    }

    private LoginRequest loginRequest(String identifier, String password) {
        return LoginRequest.builder()
                .identifier(identifier)
                .password(password)
                .build();
    }

    private User activeUser(UUID userId, String email, String username) {
        return User.builder()
                .id(userId)
                .email(email)
                .username(username)
                .passwordHash("stored-hash")
                .firstName("Cash")
                .lastName("ier")
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .failedLoginAttempts(0)
                .build();
    }

    private Role role(UUID roleId, String code, long rank) {
        return Role.builder()
                .id(roleId)
                .code(code)
                .name(code)
                .rank(rank)
                .isActive(true)
                .build();
    }
}
