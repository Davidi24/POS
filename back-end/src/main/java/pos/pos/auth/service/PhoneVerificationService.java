package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import pos.pos.auth.dto.VerifyPhoneRequest;
import pos.pos.auth.entity.AuthSmsOtpCode;
import pos.pos.auth.enums.SmsOtpPurpose;
import pos.pos.auth.repository.AuthSmsOtpCodeRepository;
import pos.pos.config.properties.SmsAuthProperties;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.auth.InvalidTokenException;
import pos.pos.exception.auth.TooManyRequestsException;
import pos.pos.exception.user.UserNotFoundException;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;


// checked
// tested
@Service
@RequiredArgsConstructor
public class PhoneVerificationService {

    private static final String TOO_MANY_PHONE_VERIFICATION_REQUESTS_MESSAGE =
            "Too many phone verification requests. Try again later.";

    private final UserRepository userRepository;
    private final AuthSmsOtpCodeRepository authSmsOtpCodeRepository;
    private final SmsAuthProperties smsAuthProperties;
    private final OneTimeCodeService oneTimeCodeService;
    private final SmsMessageService smsMessageService;

    //  1. Find the user — must be active
    //  2. User must have a phone number set
    //  3. If phone already verified → do nothing
    //  4. If a code was sent in the last 1 minute → do nothing (cooldown)
    //  5. If 15+ codes sent in the last 24 hours → do nothing (daily limit)
    //  6. Delete any existing codes for this user
    //  7. Generate a new 6-digit code, save the hash to DB
    //  8. Send the code via SMS
    @Transactional
    public void requestPhoneVerification(UUID userId) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (!StringUtils.hasText(user.getPhone())) {
            throw new AuthException("A phone number is required before it can be verified", HttpStatus.BAD_REQUEST);
        }

        if (user.isPhoneVerified()) {
            return;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime cooldownCutoff = now.minus(smsAuthProperties.getRequestCooldown());
        // allows only one code to be sent in one minute so if another request come in the same minute no code will be sent
        if (authSmsOtpCodeRepository.existsByUserIdAndPurposeAndCreatedAtAfter(
                user.getId(),
                SmsOtpPurpose.PHONE_VERIFICATION,
                cooldownCutoff
        )) {
            throw new TooManyRequestsException(TOO_MANY_PHONE_VERIFICATION_REQUESTS_MESSAGE);
        }

        // checks if ti has surpassed the daily limit
        OffsetDateTime dailyCutoff = now.minusHours(24);
        if (authSmsOtpCodeRepository.countByUserIdAndPurposeAndCreatedAtAfter(
                user.getId(),
                SmsOtpPurpose.PHONE_VERIFICATION,
                dailyCutoff
        ) >= smsAuthProperties.getDailyRequestLimit()) {
            throw new TooManyRequestsException(TOO_MANY_PHONE_VERIFICATION_REQUESTS_MESSAGE);
        }
        //Deletes all existing phone verification codes for this user before creating a new one — so only one active code exists at a time.
        authSmsOtpCodeRepository.deleteByUserIdAndPurpose(user.getId(), SmsOtpPurpose.PHONE_VERIFICATION);

        OneTimeCodeService.IssuedCode issuedCode =
                oneTimeCodeService.issueNumericCode(smsAuthProperties.getCodeLength(), smsAuthProperties.getCodePepper());

        // saved the code that was sent in the db
        authSmsOtpCodeRepository.save(AuthSmsOtpCode.builder()
                .userId(user.getId())
                .purpose(SmsOtpPurpose.PHONE_VERIFICATION)
                .phoneNumberSnapshot(user.getNormalizedPhone())
                .codeHash(issuedCode.codeHash())
                .expiresAt(now.plus(smsAuthProperties.getPhoneVerificationCodeTtl()))
                .build());

        smsMessageService.sendPhoneVerificationCode(
                user.getPhone(),
                user.getFirstName(),
                issuedCode.rawCode(),
                smsAuthProperties.getPhoneVerificationCodeTtl()
        );
    }

    // Verifies the user's phone number using the SMS code they received.
    // The code must be unused, not expired, and the phone number must match.
    // After 5 wrong attempts the code is invalidated.
    @Transactional
    public void verifyPhone(UUID userId, VerifyPhoneRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        User user = userRepository.findActiveById(userId)
                .orElseThrow(UserNotFoundException::new);

        AuthSmsOtpCode code = authSmsOtpCodeRepository
                .findTopByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        userId,
                        SmsOtpPurpose.PHONE_VERIFICATION,
                        now
                )
                .orElseThrow(InvalidTokenException::new);

        String currentPhone = user.getNormalizedPhone();
        if (currentPhone == null || !currentPhone.equals(code.getPhoneNumberSnapshot())) {
            throw new InvalidTokenException();
        }

        if (!code.getCodeHash().equals(oneTimeCodeService.hash(request.getCode(), smsAuthProperties.getCodePepper()))) {
            registerFailedAttempt(code, now);
            throw new InvalidTokenException();
        }

        user.setPhoneVerified(true);
        user.setPhoneVerifiedAt(now);
        userRepository.save(user);

        code.setUsedAt(now);
        authSmsOtpCodeRepository.save(code);
    }

    // Increments the failed attempt counter on the code.
    // If the max attempts (5) is reached, the code is marked as used and invalidated.
    private void registerFailedAttempt(AuthSmsOtpCode code, OffsetDateTime now) {
        int failedAttempts = code.getFailedAttempts() + 1;
        code.setFailedAttempts(failedAttempts);

        if (failedAttempts >= smsAuthProperties.getMaxAttempts()) {
            code.setUsedAt(now);
        }

        authSmsOtpCodeRepository.save(code);
    }
}
