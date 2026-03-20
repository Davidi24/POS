package pos.pos.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import pos.pos.auth.dto.ChangePasswordRequest;
import pos.pos.auth.dto.ForgotPasswordRequest;
import pos.pos.auth.dto.ResendVerificationRequest;
import pos.pos.auth.dto.ResetPasswordRequest;
import pos.pos.auth.dto.VerifyEmailRequest;
import pos.pos.auth.mapper.AuthMapper;
import pos.pos.auth.mapper.UserSessionMapper;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.exception.auth.InvalidTokenException;
import pos.pos.exception.user.UserNotFoundException;
import pos.pos.security.config.JwtProperties;
import pos.pos.security.service.JwtService;
import pos.pos.security.service.PasswordService;
import pos.pos.support.AuthTestDataFactory;
import pos.pos.user.entity.User;
import pos.pos.user.mapper.UserMapper;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserSessionRepository;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServicePasswordAndEmailTest {

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
    void changePassword_shouldHashSaveAndLogoutAll() {
        User user = AuthTestDataFactory.user();
        ChangePasswordRequest request = AuthTestDataFactory.validChangePasswordRequest();

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordService.matches(request.getCurrentPassword(), user.getPasswordHash())).thenReturn(true);
        when(passwordService.hash(request.getNewPassword())).thenReturn("new-hash");
        doNothing().when(authService).logoutAll(user.getId());

        authService.changePassword(user.getId(), request);

        assertEquals("new-hash", user.getPasswordHash());
        verify(userRepository).save(user);
        verify(authService).logoutAll(user.getId());
    }

    @Test
    void changePassword_shouldThrowWhenUserMissing() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> authService.changePassword(userId, AuthTestDataFactory.validChangePasswordRequest()));
    }

    @Test
    void changePassword_shouldThrowWhenCurrentPasswordInvalid() {
        User user = AuthTestDataFactory.user();
        ChangePasswordRequest request = AuthTestDataFactory.validChangePasswordRequest();

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordService.matches(request.getCurrentPassword(), user.getPasswordHash())).thenReturn(false);

        assertThrows(InvalidCredentialsException.class, () -> authService.changePassword(user.getId(), request));
    }

    @Test
    void forgotPassword_shouldSendMailWhenUserExists() {
        ForgotPasswordRequest request = AuthTestDataFactory.validForgotPasswordRequest();
        User user = AuthTestDataFactory.user();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user.getId())).thenReturn("reset-token");

        authService.forgotPassword(request);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void forgotPassword_shouldDoNothingWhenUserMissing() {
        ForgotPasswordRequest request = AuthTestDataFactory.validForgotPasswordRequest();
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());

        authService.forgotPassword(request);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void resetPassword_shouldHashSaveAndLogoutAll() {
        User user = AuthTestDataFactory.user();
        ResetPasswordRequest request = AuthTestDataFactory.validResetPasswordRequest();

        when(jwtService.isValid(request.getToken())).thenReturn(true);
        when(jwtService.extractUserId(request.getToken())).thenReturn(user.getId());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordService.hash(request.getNewPassword())).thenReturn("new-hash");
        doNothing().when(authService).logoutAll(user.getId());

        authService.resetPassword(request);

        assertEquals("new-hash", user.getPasswordHash());
        verify(userRepository).save(user);
        verify(authService).logoutAll(user.getId());
    }

    @Test
    void resetPassword_shouldThrowOnInvalidToken() {
        ResetPasswordRequest request = AuthTestDataFactory.validResetPasswordRequest();
        when(jwtService.isValid(request.getToken())).thenReturn(false);

        assertThrows(InvalidTokenException.class, () -> authService.resetPassword(request));
    }

    @Test
    void verifyEmail_shouldMarkUserVerified() {
        User user = AuthTestDataFactory.user();
        VerifyEmailRequest request = AuthTestDataFactory.validVerifyEmailRequest();

        when(jwtService.isValid(request.getToken())).thenReturn(true);
        when(jwtService.extractUserId(request.getToken())).thenReturn(user.getId());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        authService.verifyEmail(request);

        assertEquals(Boolean.TRUE, user.getEmailVerified());
        verify(userRepository).save(user);
    }

    @Test
    void verifyEmail_shouldReturnWhenAlreadyVerified() {
        User user = AuthTestDataFactory.verifiedUser();
        VerifyEmailRequest request = AuthTestDataFactory.validVerifyEmailRequest();

        when(jwtService.isValid(request.getToken())).thenReturn(true);
        when(jwtService.extractUserId(request.getToken())).thenReturn(user.getId());
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        authService.verifyEmail(request);

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resendVerification_shouldSendMailForUnverifiedUser() {
        ResendVerificationRequest request = AuthTestDataFactory.validResendVerificationRequest();
        User user = AuthTestDataFactory.user();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user.getId())).thenReturn("verify-token");

        authService.resendVerification(request);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void resendVerification_shouldSkipVerifiedUser() {
        ResendVerificationRequest request = AuthTestDataFactory.validResendVerificationRequest();
        User user = AuthTestDataFactory.verifiedUser();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));

        authService.resendVerification(request);

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    private void setField(String name, Object value) throws Exception {
        Field field = AuthService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(authService, value);
    }
}
