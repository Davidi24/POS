package pos.pos.unit.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import pos.pos.auth.dto.ForgotPasswordRequest;
import pos.pos.auth.dto.ResetPasswordWithCodeRequest;
import pos.pos.auth.entity.AuthPasswordResetToken;
import pos.pos.auth.entity.AuthSmsOtpCode;
import pos.pos.auth.enums.ClientLinkTarget;
import pos.pos.auth.enums.RecoveryChannel;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.enums.SmsOtpPurpose;
import pos.pos.auth.repository.AuthPasswordResetTokenRepository;
import pos.pos.auth.repository.AuthSmsOtpCodeRepository;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.auth.service.AuthMailService;
import pos.pos.auth.service.OneTimeCodeService;
import pos.pos.auth.service.PasswordResetService;
import pos.pos.auth.service.SmsMessageService;
import pos.pos.config.properties.FrontendProperties;
import pos.pos.config.properties.PasswordResetProperties;
import pos.pos.config.properties.SmsAuthProperties;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.auth.InvalidTokenException;
import pos.pos.exception.auth.TooManyRequestsException;
import pos.pos.role.repository.RoleRepository;
import pos.pos.security.service.OpaqueTokenService;
import pos.pos.security.service.PasswordService;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetService")
class PasswordResetServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000021");

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthPasswordResetTokenRepository authPasswordResetTokenRepository;

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private PasswordService passwordService;

    @Mock
    private OpaqueTokenService opaqueTokenService;

    @Mock
    private FrontendProperties frontendProperties;

    @Mock
    private AuthMailService authMailService;

    @Mock
    private AuthSmsOtpCodeRepository authSmsOtpCodeRepository;

    @Mock
    private OneTimeCodeService oneTimeCodeService;

    @Mock
    private SmsMessageService smsMessageService;

    @Mock
    private RoleRepository roleRepository;

    private PasswordResetService passwordResetService;

    @BeforeEach
    void setUp() {
        PasswordResetProperties passwordResetProperties = new PasswordResetProperties();
        passwordResetProperties.setTokenTtl(Duration.ofMinutes(30));
        passwordResetProperties.setRequestCooldown(Duration.ofMinutes(2));
        passwordResetProperties.setTokenPepper("reset-token-pepper");
        passwordResetProperties.setResetPath("/reset-password");
        passwordResetProperties.setSubject("Reset your POS password");

        SmsAuthProperties smsAuthProperties = new SmsAuthProperties();
        smsAuthProperties.setRequestCooldown(Duration.ofMinutes(1));
        smsAuthProperties.setPasswordResetCodeTtl(Duration.ofMinutes(10));
        smsAuthProperties.setPhoneVerificationCodeTtl(Duration.ofMinutes(10));
        smsAuthProperties.setDailyRequestLimit(15);
        smsAuthProperties.setCodeLength(6);
        smsAuthProperties.setMaxAttempts(5);
        smsAuthProperties.setCodePepper("sms-code-pepper");
        smsAuthProperties.setRestrictedPasswordResetRoleCodes(List.of("SUPER_ADMIN", "OWNER"));

        passwordResetService = new PasswordResetService(
                userRepository,
                authPasswordResetTokenRepository,
                userSessionRepository,
                passwordService,
                opaqueTokenService,
                frontendProperties,
                authMailService,
                passwordResetProperties,
                smsAuthProperties,
                authSmsOtpCodeRepository,
                oneTimeCodeService,
                smsMessageService,
                roleRepository
        );
    }

    @Nested
    @DisplayName("requestReset()")
    class RequestResetTests {

        @Test
        @DisplayName("Should issue a password reset token and send the email reset link")
        void shouldIssuePasswordResetTokenAndSendEmailResetLink() {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setEmail("  Cashier@POS.local ");
            request.setClientTarget(ClientLinkTarget.WEB);

            User user = emailUser();

            when(userRepository.findByEmailAndDeletedAtIsNull("cashier@pos.local")).thenReturn(Optional.of(user));
            when(authPasswordResetTokenRepository.existsByUserIdAndCreatedAtAfter(eq(USER_ID), any(OffsetDateTime.class)))
                    .thenReturn(false);
            when(opaqueTokenService.issue("reset-token-pepper"))
                    .thenReturn(new OpaqueTokenService.IssuedToken("raw-reset-token", "hashed-reset-token"));
            when(frontendProperties.resolveBaseUrl(ClientLinkTarget.WEB)).thenReturn("https://web.pos.local");

            passwordResetService.requestReset(request);

            verify(authPasswordResetTokenRepository).deleteByUserId(USER_ID);

            ArgumentCaptor<AuthPasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(AuthPasswordResetToken.class);
            verify(authPasswordResetTokenRepository).save(tokenCaptor.capture());
            AuthPasswordResetToken savedToken = tokenCaptor.getValue();
            assertThat(savedToken.getUserId()).isEqualTo(USER_ID);
            assertThat(savedToken.getTokenHash()).isEqualTo("hashed-reset-token");
            assertThat(savedToken.getExpiresAt()).isNotNull();

            verify(authMailService).sendPasswordResetEmail(
                    "cashier@pos.local",
                    "Casey",
                    "Reset your POS password",
                    "https://web.pos.local/reset-password?token=raw-reset-token",
                    Duration.ofMinutes(30)
            );
        }

        @Test
        @DisplayName("Should look up the user by normalized phone and send a password reset SMS")
        void shouldLookUpUserByNormalizedPhoneAndSendPasswordResetSms() {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setChannel(RecoveryChannel.SMS);
            request.setPhone(" +49 (555) 01-00 ");

            User user = phoneUser();

            when(userRepository.findByNormalizedPhoneAndDeletedAtIsNull("+495550100")).thenReturn(Optional.of(user));
            when(smsMessageService.isEnabled()).thenReturn(true);
            when(roleRepository.findActiveRoleCodesByUserId(USER_ID)).thenReturn(List.of("CASHIER"));
            when(authSmsOtpCodeRepository.existsByUserIdAndPurposeAndCreatedAtAfter(
                    eq(USER_ID),
                    eq(SmsOtpPurpose.PASSWORD_RESET),
                    any(OffsetDateTime.class)
            )).thenReturn(false);
            when(authSmsOtpCodeRepository.countByUserIdAndPurposeAndCreatedAtAfter(
                    eq(USER_ID),
                    eq(SmsOtpPurpose.PASSWORD_RESET),
                    any(OffsetDateTime.class)
            )).thenReturn(0);
            when(oneTimeCodeService.issueNumericCode(6, "sms-code-pepper"))
                    .thenReturn(new OneTimeCodeService.IssuedCode("123456", "hashed-code"));

            passwordResetService.requestReset(request);

            verify(userRepository).findByNormalizedPhoneAndDeletedAtIsNull("+495550100");
            verify(authSmsOtpCodeRepository).invalidateActiveCodes(
                    eq(USER_ID),
                    eq(SmsOtpPurpose.PASSWORD_RESET),
                    any(OffsetDateTime.class)
            );

            ArgumentCaptor<AuthSmsOtpCode> codeCaptor = ArgumentCaptor.forClass(AuthSmsOtpCode.class);
            verify(authSmsOtpCodeRepository).save(codeCaptor.capture());
            AuthSmsOtpCode savedCode = codeCaptor.getValue();
            assertThat(savedCode.getUserId()).isEqualTo(USER_ID);
            assertThat(savedCode.getPurpose()).isEqualTo(SmsOtpPurpose.PASSWORD_RESET);
            assertThat(savedCode.getPhoneNumberSnapshot()).isEqualTo("+495550100");
            assertThat(savedCode.getCodeHash()).isEqualTo("hashed-code");
            assertThat(savedCode.getExpiresAt()).isNotNull();

            verify(smsMessageService).sendPasswordResetCode(
                    "+49 555 01-00",
                    "Casey",
                    "123456",
                    Duration.ofMinutes(10)
            );
        }

        @Test
        @DisplayName("Should reject SMS reset requests when the phone number is missing")
        void shouldRejectSmsResetRequestsWhenPhoneIsMissing() {
            ForgotPasswordRequest request = new ForgotPasswordRequest();
            request.setChannel(RecoveryChannel.SMS);
            request.setPhone("   ");

            assertThatThrownBy(() -> passwordResetService.requestReset(request))
                    .isInstanceOf(AuthException.class)
                    .hasMessage("Phone is required for SMS password reset")
                    .satisfies(throwable -> assertThat(((AuthException) throwable).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));

            verifyNoInteractions(
                    userRepository,
                    authPasswordResetTokenRepository,
                    authSmsOtpCodeRepository,
                    oneTimeCodeService,
                    smsMessageService,
                    roleRepository
            );
        }
    }

    @Nested
    @DisplayName("issueAdminReset()")
    class IssueAdminResetTests {

        @Test
        @DisplayName("Should send an email reset for an active verified user")
        void shouldSendEmailResetForActiveVerifiedUser() {
            User user = emailUser();

            when(opaqueTokenService.issue("reset-token-pepper"))
                    .thenReturn(new OpaqueTokenService.IssuedToken("raw-reset-token", "hashed-reset-token"));
            when(frontendProperties.resolveBaseUrl(ClientLinkTarget.MOBILE)).thenReturn("pos://reset");

            passwordResetService.issueAdminReset(user, RecoveryChannel.EMAIL, ClientLinkTarget.MOBILE);

            verify(authPasswordResetTokenRepository).deleteByUserId(USER_ID);
            verify(authPasswordResetTokenRepository).save(any(AuthPasswordResetToken.class));
            verify(authMailService).sendPasswordResetEmail(
                    "cashier@pos.local",
                    "Casey",
                    "Reset your POS password",
                    "pos://reset/reset-password?token=raw-reset-token",
                    Duration.ofMinutes(30)
            );
        }

        @Test
        @DisplayName("Should reject email admin reset when the email is not verified")
        void shouldRejectEmailAdminResetWhenEmailIsNotVerified() {
            User user = emailUser();
            user.setEmailVerified(false);

            assertThatThrownBy(() -> passwordResetService.issueAdminReset(user, RecoveryChannel.EMAIL, ClientLinkTarget.WEB))
                    .isInstanceOf(AuthException.class)
                    .hasMessage("User must have a verified email before sending a password reset email");

            verifyNoInteractions(authMailService);
        }

        @Test
        @DisplayName("Should reject SMS admin reset when the cooldown is active")
        void shouldRejectSmsAdminResetWhenCooldownIsActive() {
            User user = phoneUser();

            when(smsMessageService.isEnabled()).thenReturn(true);
            when(roleRepository.findActiveRoleCodesByUserId(USER_ID)).thenReturn(List.of("CASHIER"));
            when(authSmsOtpCodeRepository.existsByUserIdAndPurposeAndCreatedAtAfter(
                    eq(USER_ID),
                    eq(SmsOtpPurpose.PASSWORD_RESET),
                    any(OffsetDateTime.class)
            )).thenReturn(true);

            assertThatThrownBy(() -> passwordResetService.issueAdminReset(user, RecoveryChannel.SMS, ClientLinkTarget.UNIVERSAL))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessage("Too many password reset requests. Try again later.");

            verify(authSmsOtpCodeRepository, never()).deleteByUserIdAndPurpose(any(UUID.class), any(SmsOtpPurpose.class));
            verifyNoInteractions(oneTimeCodeService);
        }
    }

    @Nested
    @DisplayName("issueRestaurantOwnerInvite()")
    class IssueRestaurantOwnerInviteTests {

        @Test
        @DisplayName("Should send an account setup email for an active owner")
        void shouldSendAccountSetupEmailForActiveOwner() {
            User user = emailUser();
            user.setEmailVerified(false);

            when(opaqueTokenService.issue("reset-token-pepper"))
                    .thenReturn(new OpaqueTokenService.IssuedToken("setup-token", "hashed-setup-token"));
            when(frontendProperties.resolveBaseUrl(ClientLinkTarget.UNIVERSAL)).thenReturn("https://links.pos.local");

            passwordResetService.issueRestaurantOwnerInvite(user, ClientLinkTarget.UNIVERSAL, "Main Restaurant");

            verify(authPasswordResetTokenRepository).deleteByUserId(USER_ID);
            verify(authPasswordResetTokenRepository).save(any(AuthPasswordResetToken.class));
            verify(authMailService).sendAccountSetupEmail(
                    "cashier@pos.local",
                    "Casey",
                    "https://links.pos.local/reset-password?token=setup-token",
                    Duration.ofMinutes(30),
                    "Main Restaurant"
            );
        }
    }

    @Nested
    @DisplayName("resetPassword()")
    class ResetPasswordTests {

        @Test
        @DisplayName("Should reset the password using a valid email reset token")
        void shouldResetPasswordUsingValidEmailResetToken() {
            pos.pos.auth.dto.ResetPasswordRequest request = new pos.pos.auth.dto.ResetPasswordRequest();
            request.setToken("raw-reset-token");
            request.setNewPassword("NewSecurePass1!");

            AuthPasswordResetToken token = AuthPasswordResetToken.builder()
                    .userId(USER_ID)
                    .tokenHash("hashed-reset-token")
                    .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30))
                    .build();

            User user = emailUser();
            user.setPasswordHash("old-hash");
            user.setFailedLoginAttempts(4);
            user.setLockedUntil(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));

            when(opaqueTokenService.hash("raw-reset-token", "reset-token-pepper")).thenReturn("hashed-reset-token");
            when(authPasswordResetTokenRepository.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
                    eq("hashed-reset-token"),
                    any(OffsetDateTime.class)
            )).thenReturn(Optional.of(token));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordService.hash("NewSecurePass1!")).thenReturn("new-hash");

            passwordResetService.resetPassword(request);

            assertThat(user.getPasswordHash()).isEqualTo("new-hash");
            assertThat(user.getPasswordUpdatedAt()).isNotNull();
            assertThat(user.getFailedLoginAttempts()).isZero();
            assertThat(user.getLockedUntil()).isNull();

            verify(userRepository).save(user);
            verify(userSessionRepository).revokeAllActiveSessionsByUserId(
                    eq(USER_ID),
                    any(OffsetDateTime.class),
                    eq(SessionRevocationReason.PASSWORD_RESET.name())
            );
            verify(authMailService).sendPasswordChangedNotificationEmail("cashier@pos.local", "Casey");
            assertThat(token.getUsedAt()).isNotNull();
            verify(authPasswordResetTokenRepository).save(token);
        }

        @Test
        @DisplayName("Should verify the email when an invited owner sets the first password")
        void shouldVerifyEmailWhenInvitedOwnerSetsFirstPassword() {
            pos.pos.auth.dto.ResetPasswordRequest request = new pos.pos.auth.dto.ResetPasswordRequest();
            request.setToken("raw-reset-token");
            request.setNewPassword("NewSecurePass1!");

            AuthPasswordResetToken token = AuthPasswordResetToken.builder()
                    .userId(USER_ID)
                    .tokenHash("hashed-reset-token")
                    .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30))
                    .build();

            User user = emailUser();
            user.setEmailVerified(false);
            user.setEmailVerifiedAt(null);

            when(opaqueTokenService.hash("raw-reset-token", "reset-token-pepper")).thenReturn("hashed-reset-token");
            when(authPasswordResetTokenRepository.findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(
                    eq("hashed-reset-token"),
                    any(OffsetDateTime.class)
            )).thenReturn(Optional.of(token));
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(passwordService.hash("NewSecurePass1!")).thenReturn("new-hash");

            passwordResetService.resetPassword(request);

            assertThat(user.isEmailVerified()).isTrue();
            assertThat(user.getEmailVerifiedAt()).isNotNull();
            verify(userRepository).save(user);
        }
    }

    @Nested
    @DisplayName("resetPasswordWithCode()")
    class ResetPasswordWithCodeTests {

        @Test
        @DisplayName("Should reset the password using a normalized phone lookup")
        void shouldResetPasswordUsingNormalizedPhoneLookup() {
            ResetPasswordWithCodeRequest request = new ResetPasswordWithCodeRequest();
            request.setPhone(" +49 (555) 01-00 ");
            request.setCode("123456");
            request.setNewPassword("NewSecurePass1!");

            User user = phoneUser();
            user.setPasswordHash("old-hash");
            user.setFailedLoginAttempts(3);
            user.setLockedUntil(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5));

            AuthSmsOtpCode code = AuthSmsOtpCode.builder()
                    .userId(USER_ID)
                    .purpose(SmsOtpPurpose.PASSWORD_RESET)
                    .phoneNumberSnapshot("+495550100")
                    .codeHash("hashed-code")
                    .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10))
                    .build();

            when(userRepository.findByNormalizedPhoneAndDeletedAtIsNull("+495550100")).thenReturn(Optional.of(user));
            when(authSmsOtpCodeRepository.findTopByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                    eq(USER_ID),
                    eq(SmsOtpPurpose.PASSWORD_RESET),
                    any(OffsetDateTime.class)
            )).thenReturn(Optional.of(code));
            when(oneTimeCodeService.hash("123456", "sms-code-pepper")).thenReturn("hashed-code");
            when(passwordService.hash("NewSecurePass1!")).thenReturn("new-hash");

            passwordResetService.resetPasswordWithCode(request);

            assertThat(user.getPasswordHash()).isEqualTo("new-hash");
            assertThat(user.getPasswordUpdatedAt()).isNotNull();
            assertThat(user.getFailedLoginAttempts()).isZero();
            assertThat(user.getLockedUntil()).isNull();

            verify(userRepository).findByNormalizedPhoneAndDeletedAtIsNull("+495550100");
            verify(userRepository).save(user);
            verify(userSessionRepository).revokeAllActiveSessionsByUserId(
                    eq(USER_ID),
                    any(OffsetDateTime.class),
                    eq(SessionRevocationReason.PASSWORD_RESET.name())
            );
            verify(authMailService).sendPasswordChangedNotificationEmail("cashier@pos.local", "Casey");

            ArgumentCaptor<AuthSmsOtpCode> codeCaptor = ArgumentCaptor.forClass(AuthSmsOtpCode.class);
            verify(authSmsOtpCodeRepository).save(codeCaptor.capture());
            assertThat(codeCaptor.getValue().getUsedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should invalidate the SMS code after max failed attempts")
        void shouldInvalidateSmsCodeAfterMaxFailedAttempts() {
            ResetPasswordWithCodeRequest request = new ResetPasswordWithCodeRequest();
            request.setPhone(" +49 (555) 01-00 ");
            request.setCode("000000");
            request.setNewPassword("NewSecurePass1!");

            User user = phoneUser();
            AuthSmsOtpCode code = AuthSmsOtpCode.builder()
                    .userId(USER_ID)
                    .purpose(SmsOtpPurpose.PASSWORD_RESET)
                    .phoneNumberSnapshot("+495550100")
                    .codeHash("hashed-code")
                    .failedAttempts(4)
                    .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10))
                    .build();

            when(userRepository.findByNormalizedPhoneAndDeletedAtIsNull("+495550100")).thenReturn(Optional.of(user));
            when(authSmsOtpCodeRepository.findTopByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                    eq(USER_ID),
                    eq(SmsOtpPurpose.PASSWORD_RESET),
                    any(OffsetDateTime.class)
            )).thenReturn(Optional.of(code));
            when(oneTimeCodeService.hash("000000", "sms-code-pepper")).thenReturn("wrong-hash");

            assertThatThrownBy(() -> passwordResetService.resetPasswordWithCode(request))
                    .isInstanceOf(InvalidTokenException.class);

            assertThat(code.getFailedAttempts()).isEqualTo(5);
            assertThat(code.getUsedAt()).isNotNull();
            verify(authSmsOtpCodeRepository).save(code);
            verify(userRepository, never()).save(any(User.class));
        }
    }

    private User emailUser() {
        return User.builder()
                .id(USER_ID)
                .email("cashier@pos.local")
                .passwordHash("stored-hash")
                .firstName("Casey")
                .lastName("Cashier")
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .build();
    }

    private User phoneUser() {
        return User.builder()
                .id(USER_ID)
                .email("cashier@pos.local")
                .passwordHash("stored-hash")
                .firstName("Casey")
                .lastName("Cashier")
                .phone("+49 555 01-00")
                .normalizedPhone("+495550100")
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .phoneVerified(true)
                .build();
    }
}
