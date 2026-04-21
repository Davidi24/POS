package pos.pos.unit.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.auth.dto.VerifyPhoneRequest;
import pos.pos.auth.entity.AuthSmsOtpCode;
import pos.pos.auth.enums.SmsOtpPurpose;
import pos.pos.auth.repository.AuthSmsOtpCodeRepository;
import pos.pos.auth.service.OneTimeCodeService;
import pos.pos.auth.service.PhoneVerificationService;
import pos.pos.auth.service.SmsMessageService;
import pos.pos.config.properties.SmsAuthProperties;
import pos.pos.exception.auth.InvalidTokenException;
import pos.pos.exception.auth.TooManyRequestsException;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
@DisplayName("PhoneVerificationService")
class PhoneVerificationServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000041");
    private static final String TOO_MANY_REQUESTS_MESSAGE = "Too many phone verification requests. Try again later.";

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuthSmsOtpCodeRepository authSmsOtpCodeRepository;

    @Mock
    private OneTimeCodeService oneTimeCodeService;

    @Mock
    private SmsMessageService smsMessageService;

    private PhoneVerificationService phoneVerificationService;

    @BeforeEach
    void setUp() {
        SmsAuthProperties smsAuthProperties = new SmsAuthProperties();
        smsAuthProperties.setRequestCooldown(Duration.ofMinutes(1));
        smsAuthProperties.setDailyRequestLimit(15);
        smsAuthProperties.setCodeLength(6);
        smsAuthProperties.setCodePepper("sms-code-pepper");
        smsAuthProperties.setPhoneVerificationCodeTtl(Duration.ofMinutes(10));
        smsAuthProperties.setPasswordResetCodeTtl(Duration.ofMinutes(10));
        smsAuthProperties.setMaxAttempts(5);

        phoneVerificationService = new PhoneVerificationService(
                userRepository,
                authSmsOtpCodeRepository,
                smsAuthProperties,
                oneTimeCodeService,
                smsMessageService
        );
    }

    @Nested
    @DisplayName("requestPhoneVerification()")
    class RequestPhoneVerificationTests {

        @Test
        @DisplayName("Should send phone verification code when request is allowed")
        void shouldSendPhoneVerificationCodeWhenRequestIsAllowed() {
            User user = phoneUser();

            when(userRepository.findActiveById(USER_ID)).thenReturn(Optional.of(user));
            when(authSmsOtpCodeRepository.existsByUserIdAndPurposeAndCreatedAtAfter(
                    eq(USER_ID),
                    eq(SmsOtpPurpose.PHONE_VERIFICATION),
                    any(OffsetDateTime.class)
            )).thenReturn(false);
            when(authSmsOtpCodeRepository.countByUserIdAndPurposeAndCreatedAtAfter(
                    eq(USER_ID),
                    eq(SmsOtpPurpose.PHONE_VERIFICATION),
                    any(OffsetDateTime.class)
            )).thenReturn(0);
            when(oneTimeCodeService.issueNumericCode(6, "sms-code-pepper"))
                    .thenReturn(new OneTimeCodeService.IssuedCode("123456", "hashed-code"));

            phoneVerificationService.requestPhoneVerification(USER_ID);

            verify(authSmsOtpCodeRepository).invalidateActiveCodes(
                    eq(USER_ID),
                    eq(SmsOtpPurpose.PHONE_VERIFICATION),
                    any(OffsetDateTime.class)
            );

            ArgumentCaptor<AuthSmsOtpCode> codeCaptor = ArgumentCaptor.forClass(AuthSmsOtpCode.class);
            verify(authSmsOtpCodeRepository).save(codeCaptor.capture());
            AuthSmsOtpCode savedCode = codeCaptor.getValue();
            assertThat(savedCode.getUserId()).isEqualTo(USER_ID);
            assertThat(savedCode.getPurpose()).isEqualTo(SmsOtpPurpose.PHONE_VERIFICATION);
            assertThat(savedCode.getPhoneNumberSnapshot()).isEqualTo("+495550100");
            assertThat(savedCode.getCodeHash()).isEqualTo("hashed-code");

            verify(smsMessageService).sendPhoneVerificationCode(
                    "+49 555 01-00",
                    "Casey",
                    "123456",
                    Duration.ofMinutes(10)
            );
        }

        @Test
        @DisplayName("Should throw 429 when cooldown is active")
        void shouldThrow429WhenCooldownIsActive() {
            User user = phoneUser();

            when(userRepository.findActiveById(USER_ID)).thenReturn(Optional.of(user));
            when(authSmsOtpCodeRepository.existsByUserIdAndPurposeAndCreatedAtAfter(
                    eq(USER_ID),
                    eq(SmsOtpPurpose.PHONE_VERIFICATION),
                    any(OffsetDateTime.class)
            )).thenReturn(true);

            assertThatThrownBy(() -> phoneVerificationService.requestPhoneVerification(USER_ID))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessage(TOO_MANY_REQUESTS_MESSAGE);

            verify(authSmsOtpCodeRepository, never()).invalidateActiveCodes(
                    any(UUID.class),
                    any(SmsOtpPurpose.class),
                    any(OffsetDateTime.class)
            );
            verifyNoInteractions(oneTimeCodeService, smsMessageService);
        }

        @Test
        @DisplayName("Should throw 429 when daily request limit is reached")
        void shouldThrow429WhenDailyRequestLimitIsReached() {
            User user = phoneUser();

            when(userRepository.findActiveById(USER_ID)).thenReturn(Optional.of(user));
            when(authSmsOtpCodeRepository.existsByUserIdAndPurposeAndCreatedAtAfter(
                    eq(USER_ID),
                    eq(SmsOtpPurpose.PHONE_VERIFICATION),
                    any(OffsetDateTime.class)
            )).thenReturn(false);
            when(authSmsOtpCodeRepository.countByUserIdAndPurposeAndCreatedAtAfter(
                    eq(USER_ID),
                    eq(SmsOtpPurpose.PHONE_VERIFICATION),
                    any(OffsetDateTime.class)
            )).thenReturn(15);

            assertThatThrownBy(() -> phoneVerificationService.requestPhoneVerification(USER_ID))
                    .isInstanceOf(TooManyRequestsException.class)
                    .hasMessage(TOO_MANY_REQUESTS_MESSAGE);

            verify(authSmsOtpCodeRepository, never()).invalidateActiveCodes(
                    any(UUID.class),
                    any(SmsOtpPurpose.class),
                    any(OffsetDateTime.class)
            );
            verifyNoInteractions(oneTimeCodeService, smsMessageService);
        }
    }

    @Nested
    @DisplayName("verifyPhone()")
    class VerifyPhoneTests {

        @Test
        @DisplayName("Should verify phone and mark code used when code matches")
        void shouldVerifyPhoneAndMarkCodeUsedWhenCodeMatches() {
            User user = phoneUser();
            VerifyPhoneRequest request = new VerifyPhoneRequest();
            request.setCode("123456");

            AuthSmsOtpCode code = AuthSmsOtpCode.builder()
                    .userId(USER_ID)
                    .purpose(SmsOtpPurpose.PHONE_VERIFICATION)
                    .phoneNumberSnapshot("+495550100")
                    .codeHash("hashed-code")
                    .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10))
                    .build();

            when(userRepository.findActiveById(USER_ID)).thenReturn(Optional.of(user));
            when(authSmsOtpCodeRepository.findTopByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                    eq(USER_ID),
                    eq(SmsOtpPurpose.PHONE_VERIFICATION),
                    any(OffsetDateTime.class)
            )).thenReturn(Optional.of(code));
            when(oneTimeCodeService.hash("123456", "sms-code-pepper")).thenReturn("hashed-code");

            phoneVerificationService.verifyPhone(USER_ID, request);

            assertThat(user.isPhoneVerified()).isTrue();
            assertThat(user.getPhoneVerifiedAt()).isNotNull();
            verify(userRepository).save(user);
            assertThat(code.getUsedAt()).isNotNull();
            verify(authSmsOtpCodeRepository).save(code);
        }

        @Test
        @DisplayName("Should invalidate the code after max failed attempts")
        void shouldInvalidateTheCodeAfterMaxFailedAttempts() {
            User user = phoneUser();
            VerifyPhoneRequest request = new VerifyPhoneRequest();
            request.setCode("000000");

            AuthSmsOtpCode code = AuthSmsOtpCode.builder()
                    .userId(USER_ID)
                    .purpose(SmsOtpPurpose.PHONE_VERIFICATION)
                    .phoneNumberSnapshot("+495550100")
                    .codeHash("hashed-code")
                    .failedAttempts(4)
                    .expiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(10))
                    .build();

            when(userRepository.findActiveById(USER_ID)).thenReturn(Optional.of(user));
            when(authSmsOtpCodeRepository.findTopByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                    eq(USER_ID),
                    eq(SmsOtpPurpose.PHONE_VERIFICATION),
                    any(OffsetDateTime.class)
            )).thenReturn(Optional.of(code));
            when(oneTimeCodeService.hash("000000", "sms-code-pepper")).thenReturn("wrong-hash");

            assertThatThrownBy(() -> phoneVerificationService.verifyPhone(USER_ID, request))
                    .isInstanceOf(InvalidTokenException.class);

            assertThat(code.getFailedAttempts()).isEqualTo(5);
            assertThat(code.getUsedAt()).isNotNull();
            verify(authSmsOtpCodeRepository).save(code);
            verify(userRepository, never()).save(any(User.class));
        }
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
                .phoneVerified(false)
                .build();
    }
}
