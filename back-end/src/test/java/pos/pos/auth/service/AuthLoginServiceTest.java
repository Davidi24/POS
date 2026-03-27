package pos.pos.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import pos.pos.auth.dto.LoginRequest;
import pos.pos.auth.entity.AuthLoginAttempt;
import pos.pos.auth.enums.LoginFailureReason;
import pos.pos.auth.mapper.UserSessionMapper;
import pos.pos.auth.repository.AuthLoginAttemptRepository;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.config.JwtProperties;
import pos.pos.security.service.JwtService;
import pos.pos.security.service.PasswordService;
import pos.pos.security.service.RefreshTokenSecurityService;
import pos.pos.security.util.ClientInfo;
import pos.pos.user.entity.User;
import pos.pos.user.mapper.UserMapper;
import pos.pos.user.repository.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthLoginServiceTest {

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
        ReflectionTestUtils.setField(authLoginService, "maxAttemptsPerIp", 20);
        ReflectionTestUtils.setField(authLoginService, "maxAttemptsPerAccount", 5);
        ReflectionTestUtils.setField(authLoginService, "windowMinutes", 5L);
        ReflectionTestUtils.setField(authLoginService, "maxFailedAttempts", 5);
        ReflectionTestUtils.setField(authLoginService, "lockDurationMinutes", 15L);
        ReflectionTestUtils.setField(authLoginService, "maxActiveSessions", 3);
    }

    @Test
    void login_shouldApplyAccountRateLimitBeforePasswordVerification() {
        UUID userId = UUID.randomUUID();
        LoginRequest request = LoginRequest.builder()
                .email("cashier@pos.local")
                .password("Password123!")
                .build();
        User user = User.builder()
                .id(userId)
                .email("cashier@pos.local")
                .passwordHash("password-hash")
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .build();
        ClientInfo clientInfo = new ClientInfo("127.0.0.1", "JUnit");
        ArgumentCaptor<AuthLoginAttempt> attemptCaptor = ArgumentCaptor.forClass(AuthLoginAttempt.class);

        when(userRepository.findByEmailAndDeletedAtIsNull("cashier@pos.local")).thenReturn(Optional.of(user));
        when(authLoginAttemptRepository.countByIpAddressAndAttemptedAtAfter(any(), any())).thenReturn(0L);
        when(authLoginAttemptRepository.countByEmailAndAttemptedAtAfterAndSuccessFalse(any(), any())).thenReturn(5L);

        assertThatThrownBy(() -> authLoginService.login(request, clientInfo))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Too many login attempts. Try again later.");

        verify(authLoginAttemptRepository).save(attemptCaptor.capture());
        assertThat(attemptCaptor.getValue().getFailureReason()).isEqualTo(LoginFailureReason.ACCOUNT_RATE_LIMITED);
        verify(passwordService, never()).matches(any(), any());
    }
}
