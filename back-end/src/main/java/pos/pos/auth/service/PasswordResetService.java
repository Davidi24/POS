package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.dto.ForgotPasswordRequest;
import pos.pos.auth.dto.ResetPasswordRequest;
import pos.pos.auth.dto.ResetPasswordWithCodeRequest;
import pos.pos.auth.entity.AuthPasswordResetToken;
import pos.pos.auth.entity.AuthSmsOtpCode;
import pos.pos.auth.enums.ClientLinkTarget;
import pos.pos.auth.enums.RecoveryChannel;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.enums.SmsOtpPurpose;
import pos.pos.auth.repository.AuthSmsOtpCodeRepository;
import pos.pos.auth.repository.AuthPasswordResetTokenRepository;
import pos.pos.auth.repository.UserSessionRepository;
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
import pos.pos.utils.FrontendUrlUtils;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String TOO_MANY_PASSWORD_RESET_REQUESTS_MESSAGE =
            "Too many password reset requests. Try again later.";

    private final UserRepository userRepository;
    private final AuthPasswordResetTokenRepository authPasswordResetTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordService passwordService;
    private final OpaqueTokenService opaqueTokenService;
    private final FrontendProperties frontendProperties;
    private final AuthMailService authMailService;
    private final PasswordResetProperties passwordResetProperties;
    private final SmsAuthProperties smsAuthProperties;
    private final AuthSmsOtpCodeRepository authSmsOtpCodeRepository;
    private final OneTimeCodeService oneTimeCodeService;
    private final SmsMessageService smsMessageService;
    private final RoleRepository roleRepository;

    // Routes the reset request to email or SMS based on the channel in the request
    @Transactional
    public void requestReset(ForgotPasswordRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (request.getChannel() == RecoveryChannel.SMS) {
            requestSmsReset(request, now);
            return;
        }

        requestEmailReset(request, now);
    }

    @Transactional
    public void issueAdminReset(User user, RecoveryChannel channel, ClientLinkTarget clientTarget) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (channel == RecoveryChannel.SMS) {
            issueAdminSmsReset(user, now);
            return;
        }

        issueAdminEmailReset(user, clientTarget, now);
    }

    // Resets the password using a token from the email reset link
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String tokenHash = opaqueTokenService.hash(request.getToken(), passwordResetProperties.getTokenPepper());

        AuthPasswordResetToken resetToken = authPasswordResetTokenRepository
                .findByTokenHashAndUsedAtIsNullAndExpiresAtAfter(tokenHash, now)
                .orElseThrow(InvalidTokenException::new);

        User user = userRepository.findById(resetToken.getUserId())
                .filter(foundUser -> foundUser.getDeletedAt() == null && foundUser.isActive())
                .orElseThrow(InvalidTokenException::new);

        applyNewPassword(user, request.getNewPassword(), now, SessionRevocationReason.PASSWORD_RESET.name());

        resetToken.setUsedAt(now);
        authPasswordResetTokenRepository.save(resetToken);
    }

    // Resets the password using an SMS code — user is identified by phone number
    @Transactional(noRollbackFor = InvalidTokenException.class)
    public void resetPasswordWithCode(ResetPasswordWithCodeRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String normalizedPhone = requiredPhone(request.getPhone());

        User user = userRepository.findByNormalizedPhoneAndDeletedAtIsNull(normalizedPhone)
                .filter(User::isActive)
                .orElseThrow(InvalidTokenException::new);

        AuthSmsOtpCode code = authSmsOtpCodeRepository
                .findTopByUserIdAndPurposeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
                        user.getId(),
                        SmsOtpPurpose.PASSWORD_RESET,
                        now
                )
                .orElseThrow(InvalidTokenException::new);

        if (!user.isPhoneVerified()
                || user.getNormalizedPhone() == null
                || !user.getNormalizedPhone().equals(code.getPhoneNumberSnapshot())) {
            throw new InvalidTokenException();
        }

        if (!code.getCodeHash().equals(oneTimeCodeService.hash(request.getCode(), smsAuthProperties.getCodePepper()))) {
            registerFailedAttempt(code, now);
            throw new InvalidTokenException();
        }

        applyNewPassword(user, request.getNewPassword(), now, SessionRevocationReason.PASSWORD_RESET.name());

        code.setUsedAt(now);
        authSmsOtpCodeRepository.save(code);
    }

    // Applies the new password, revokes all active sessions, and notifies the user via email
    private void applyNewPassword(User user, String newPassword, OffsetDateTime now, String revocationReason) {
        user.setPasswordHash(passwordService.hash(newPassword));
        user.setPasswordUpdatedAt(now);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        userSessionRepository.revokeAllActiveSessionsByUserId(
                user.getId(),
                now,
                revocationReason
        );

        authMailService.sendPasswordChangedNotificationEmail(user.getEmail(), user.getFirstName());
    }

    private void requestEmailReset(ForgotPasswordRequest request, OffsetDateTime now) {
        // 1. Find user by email and validate (active + email verified)
        String normalizedEmail = requiredEmail(request.getEmail());
        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail).orElse(null);
        if (user == null || !user.isActive() || !user.isEmailVerified()) {
            return;
        }

        // 2. Check cooldown — if a reset was requested recently, do nothing
        OffsetDateTime cooldownCutoff = now.minus(passwordResetProperties.getRequestCooldown());
        if (authPasswordResetTokenRepository.existsByUserIdAndCreatedAtAfter(user.getId(), cooldownCutoff)) {
            return;
        }

        // 3. Delete old reset tokens
        authPasswordResetTokenRepository.deleteByUserId(user.getId());

        // 4. Generate a new reset token
        OpaqueTokenService.IssuedToken issuedToken = opaqueTokenService.issue(passwordResetProperties.getTokenPepper());

        authPasswordResetTokenRepository.save(
                AuthPasswordResetToken.builder()
                        .userId(user.getId())
                        .tokenHash(issuedToken.tokenHash())
                        .expiresAt(now.plus(passwordResetProperties.getTokenTtl()))
                        .build()
        );

        // 5. Send the reset link via email
        sendResetEmail(user, request.getClientTarget(), issuedToken);
    }

    private void issueAdminEmailReset(User user, ClientLinkTarget clientTarget, OffsetDateTime now) {
        if (!user.isActive() || user.getDeletedAt() != null) {
            throw new AuthException(
                    "User account must be active before sending a password reset email",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (!user.isEmailVerified()) {
            throw new AuthException(
                    "User must have a verified email before sending a password reset email",
                    HttpStatus.BAD_REQUEST
            );
        }

        authPasswordResetTokenRepository.deleteByUserId(user.getId());
        OpaqueTokenService.IssuedToken issuedToken = opaqueTokenService.issue(passwordResetProperties.getTokenPepper());

        authPasswordResetTokenRepository.save(
                AuthPasswordResetToken.builder()
                        .userId(user.getId())
                        .tokenHash(issuedToken.tokenHash())
                        .expiresAt(now.plus(passwordResetProperties.getTokenTtl()))
                        .build()
        );

        sendResetEmail(user, clientTarget, issuedToken);
    }

    private void sendResetEmail(
            User user,
            ClientLinkTarget clientTarget,
            OpaqueTokenService.IssuedToken issuedToken
    ) {
        authMailService.sendPasswordResetEmail(
                user.getEmail(),
                user.getFirstName(),
                passwordResetProperties.getSubject(),
                FrontendUrlUtils.buildTokenUrl(
                        frontendProperties.resolveBaseUrl(clientTarget),
                        passwordResetProperties.getResetPath(),
                        issuedToken.rawToken()
                ),
                passwordResetProperties.getTokenTtl()
        );
    }

    // Finds and validates the user by phone before delegating to the core SMS reset logic
    private void requestSmsReset(ForgotPasswordRequest request, OffsetDateTime now) {
        String normalizedPhone = requiredPhone(request.getPhone());
        User user = userRepository.findByNormalizedPhoneAndDeletedAtIsNull(normalizedPhone).orElse(null);
        if (user == null || !user.isActive() || !user.isPhoneVerified() || !canUseSmsReset(user)) {
            return;
        }

        requestSmsReset(user, now);
    }

    private void issueAdminSmsReset(User user, OffsetDateTime now) {
        if (!user.isActive() || user.getDeletedAt() != null) {
            throw new AuthException(
                    "User account must be active before sending an SMS password reset",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (!user.isPhoneVerified() || user.getPhone() == null || user.getNormalizedPhone() == null) {
            throw new AuthException(
                    "User must have a verified phone before sending an SMS password reset",
                    HttpStatus.BAD_REQUEST
            );
        }
        if (!canUseSmsReset(user)) {
            throw new AuthException(
                    "User is not eligible for SMS password reset",
                    HttpStatus.BAD_REQUEST
            );
        }

        OffsetDateTime cooldownCutoff = now.minus(smsAuthProperties.getRequestCooldown());
        if (authSmsOtpCodeRepository.existsByUserIdAndPurposeAndCreatedAtAfter(
                user.getId(),
                SmsOtpPurpose.PASSWORD_RESET,
                cooldownCutoff
        )) {
            throw new TooManyRequestsException(TOO_MANY_PASSWORD_RESET_REQUESTS_MESSAGE);
        }

        OffsetDateTime dailyCutoff = now.minusHours(24);
        if (authSmsOtpCodeRepository.countByUserIdAndPurposeAndCreatedAtAfter(
                user.getId(),
                SmsOtpPurpose.PASSWORD_RESET,
                dailyCutoff
        ) >= smsAuthProperties.getDailyRequestLimit()) {
            throw new TooManyRequestsException(TOO_MANY_PASSWORD_RESET_REQUESTS_MESSAGE);
        }

        requestSmsReset(user, now);
    }

    // Checks if the user is allowed to use SMS reset — SMS must be enabled, phone verified, and role not restricted
    private boolean canUseSmsReset(User user) {
        return smsMessageService.isEnabled()
                && user.isPhoneVerified()
                && user.getPhone() != null
                && roleRepository.findActiveRoleCodesByUserId(user.getId()).stream()
                .noneMatch(code -> smsAuthProperties.getRestrictedPasswordResetRoleCodes().contains(code));
    }

    private void requestSmsReset(User user, OffsetDateTime now) {
        // 1. Check cooldown — if a code was sent recently, do nothing
        OffsetDateTime cooldownCutoff = now.minus(smsAuthProperties.getRequestCooldown());
        if (authSmsOtpCodeRepository.existsByUserIdAndPurposeAndCreatedAtAfter(
                user.getId(),
                SmsOtpPurpose.PASSWORD_RESET,
                cooldownCutoff
        )) {
            return;
        }

        // 2. Check daily limit — max 15 codes per 24 hours
        OffsetDateTime dailyCutoff = now.minusHours(24);
        if (authSmsOtpCodeRepository.countByUserIdAndPurposeAndCreatedAtAfter(
                user.getId(),
                SmsOtpPurpose.PASSWORD_RESET,
                dailyCutoff
        ) >= smsAuthProperties.getDailyRequestLimit()) {
            return;
        }

        // 3. Invalidate any previous active reset code so only the latest remains usable
        authSmsOtpCodeRepository.invalidateActiveCodes(user.getId(), SmsOtpPurpose.PASSWORD_RESET, now);

        // 4. Generate a new code and save the hash to DB
        OneTimeCodeService.IssuedCode issuedCode =
                oneTimeCodeService.issueNumericCode(smsAuthProperties.getCodeLength(), smsAuthProperties.getCodePepper());

        authSmsOtpCodeRepository.save(AuthSmsOtpCode.builder()
                .userId(user.getId())
                .purpose(SmsOtpPurpose.PASSWORD_RESET)
                .phoneNumberSnapshot(user.getNormalizedPhone())
                .codeHash(issuedCode.codeHash())
                .expiresAt(now.plus(smsAuthProperties.getPasswordResetCodeTtl()))
                .build());

        // 5. Send the code via SMS
        smsMessageService.sendPasswordResetCode(
                user.getPhone(),
                user.getFirstName(),
                issuedCode.rawCode(),
                smsAuthProperties.getPasswordResetCodeTtl()
        );
    }

    // Normalizes and validates the email — throws if blank or missing
    private String requiredEmail(String email) {
        String normalizedEmail = NormalizationUtils.normalizeLower(email);
        if (normalizedEmail == null) {
            throw new AuthException("Email is required for email password reset", HttpStatus.BAD_REQUEST);
        }
        return normalizedEmail;
    }

    // Normalizes and validates the phone — throws if blank or missing
    private String requiredPhone(String phone) {
        String normalizedPhone = NormalizationUtils.normalizePhone(phone);
        if (normalizedPhone == null) {
            throw new AuthException("Phone is required for SMS password reset", HttpStatus.BAD_REQUEST);
        }
        return normalizedPhone;
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
