package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.dto.ForgotPasswordRequest;
import pos.pos.auth.dto.PasswordLinkView;
import pos.pos.auth.dto.ResetPasswordRequest;
import pos.pos.auth.dto.ResetPasswordWithCodeRequest;
import pos.pos.auth.entity.AuthPasswordResetToken;
import pos.pos.auth.entity.AuthSmsOtpCode;
import pos.pos.auth.enums.ClientLinkTarget;
import pos.pos.auth.enums.PasswordLinkPurpose;
import pos.pos.auth.enums.PasswordLinkStatus;
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

import java.time.Duration;
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
    private final pos.pos.restaurant.repository.RestaurantRepository restaurantRepository;

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

    @Transactional
    public void issueRestaurantOwnerInvite(User user, ClientLinkTarget clientTarget, String restaurantName) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (!user.isActive() || user.getDeletedAt() != null) {
            throw new AuthException(
                    "User account must be active before sending an onboarding email",
                    HttpStatus.BAD_REQUEST
            );
        }

        sendInvite(user, clientTarget, restaurantName, now);
    }

    /**
     * What an emailed link is for and whether it still works, for the page or screen where the person chooses their
     * password. Unknown links say so without telling anything about any account.
     */
    @Transactional(readOnly = true)
    public PasswordLinkView checkLink(String rawToken) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        AuthPasswordResetToken token = rawToken == null || rawToken.isBlank() ? null : authPasswordResetTokenRepository
                .findByTokenHash(opaqueTokenService.hash(rawToken.trim(), passwordResetProperties.getTokenPepper()))
                .orElse(null);
        User user = token == null ? null : userRepository.findById(token.getUserId()).orElse(null);
        PasswordLinkStatus status = statusOf(token, user, now);
        PasswordLinkPurpose purpose = token == null ? null : token.getPurpose();
        if (status != PasswordLinkStatus.VALID) {
            return new PasswordLinkView(status, purpose, linkProblem(status, purpose), null, null, null, null, null);
        }
        String restaurantName = user.getRestaurantId() == null ? null : restaurantRepository
                .findByIdAndDeletedAtIsNull(user.getRestaurantId()).map(restaurant -> restaurant.getName()).orElse(null);
        return new PasswordLinkView(status, purpose, null, user.getFirstName(), user.getUsername(), user.getEmail(),
                restaurantName, token.getExpiresAt());
    }

    /** The newest invitation link sent to this person, and what it can still do. */
    @Transactional(readOnly = true)
    public java.util.Optional<InviteLink> latestInvite(User user) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return authPasswordResetTokenRepository.findTopByUserIdAndPurposeOrderByCreatedAtDesc(user.getId(), PasswordLinkPurpose.INVITE)
                .map(token -> new InviteLink(statusOf(token, user, now), token.getExpiresAt()));
    }

    public record InviteLink(PasswordLinkStatus status, OffsetDateTime expiresAt) {
    }

    /** What to tell someone whose link no longer works, and what to do instead. */
    public static String linkProblem(PasswordLinkStatus status, PasswordLinkPurpose purpose) {
        boolean invite = purpose == PasswordLinkPurpose.INVITE;
        return switch (status) {
            case VALID -> null;
            case EXPIRED -> invite
                    ? "This invitation link has expired. Ask for a new invitation, or use \"Forgot password?\" in the POS app with your email to get a new link."
                    : "This link has expired. Use \"Forgot password?\" in the POS app to get a new one.";
            case USED -> "This link was already used, so your password is set. Sign in with it, or use \"Forgot password?\" if you don't remember it.";
            case REPLACED -> invite
                    ? "A newer invitation replaced this link. Use the link in the most recent email."
                    : "A newer email replaced this link. Use the link in the most recent email.";
            case UNAVAILABLE -> "This account has been switched off. Contact whoever set it up for you.";
            case INVALID -> "This link isn't valid. Check that you copied the whole link from the email.";
        };
    }

    // Sets the password from an emailed link: an owner's first password, or a reset
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String tokenHash = opaqueTokenService.hash(request.getToken().trim(), passwordResetProperties.getTokenPepper());

        AuthPasswordResetToken resetToken = authPasswordResetTokenRepository.findByTokenHashForUpdate(tokenHash).orElse(null);
        User user = resetToken == null ? null : userRepository.findById(resetToken.getUserId()).orElse(null);
        PasswordLinkStatus status = statusOf(resetToken, user, now);
        if (status != PasswordLinkStatus.VALID) {
            throw new AuthException(linkProblem(status, resetToken == null ? null : resetToken.getPurpose()), HttpStatus.BAD_REQUEST);
        }

        String problem = PasswordRules.problem(request.getNewPassword());
        if (problem != null) {
            throw new AuthException(problem, HttpStatus.BAD_REQUEST);
        }

        // Someone choosing their first password doesn't need a "your password was changed" email.
        boolean firstPassword = resetToken.getPurpose() == PasswordLinkPurpose.INVITE;
        applyNewPassword(user, request.getNewPassword(), now, SessionRevocationReason.PASSWORD_RESET.name(), !firstPassword);

        resetToken.setUsedAt(now);
        authPasswordResetTokenRepository.save(resetToken);
    }

    private static PasswordLinkStatus statusOf(AuthPasswordResetToken token, User user, OffsetDateTime now) {
        if (token == null) return PasswordLinkStatus.INVALID;
        if (token.getUsedAt() != null) return PasswordLinkStatus.USED;
        if (token.getSupersededAt() != null) return PasswordLinkStatus.REPLACED;
        if (!token.getExpiresAt().isAfter(now)) return PasswordLinkStatus.EXPIRED;
        if (user == null || user.getDeletedAt() != null || !user.isActive()) return PasswordLinkStatus.UNAVAILABLE;
        return PasswordLinkStatus.VALID;
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

        applyNewPassword(user, request.getNewPassword(), now, SessionRevocationReason.PASSWORD_RESET.name(), true);

        code.setUsedAt(now);
        authSmsOtpCodeRepository.save(code);
    }

    // Applies the new password, revokes all active sessions, and notifies the user via email
    private void applyNewPassword(User user, String newPassword, OffsetDateTime now, String revocationReason, boolean notify) {
        user.setPasswordHash(passwordService.hash(newPassword));
        user.setPasswordUpdatedAt(now);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        if (!user.isEmailVerified()) {
            user.setEmailVerified(true);
            user.setEmailVerifiedAt(now);
        }
        userRepository.save(user);

        userSessionRepository.revokeAllActiveSessionsByUserId(
                user.getId(),
                now,
                revocationReason
        );

        if (notify) {
            authMailService.sendPasswordChangedNotificationEmail(user.getEmail(), user.getFirstName());
        }
    }

    private void requestEmailReset(ForgotPasswordRequest request, OffsetDateTime now) {
        // 1. Find user by email and validate (active)
        String normalizedEmail = requiredEmail(request.getEmail());
        User user = userRepository.findByEmailAndDeletedAtIsNull(normalizedEmail).orElse(null);
        if (user == null || !user.isActive()) {
            return;
        }

        // 2. Check cooldown — if a link was sent recently, do nothing
        OffsetDateTime cooldownCutoff = now.minus(passwordResetProperties.getRequestCooldown());
        if (authPasswordResetTokenRepository.existsByUserIdAndCreatedAtAfter(user.getId(), cooldownCutoff)) {
            return;
        }

        // 3. An owner who never finished their invitation gets a fresh invitation; others need a confirmed email
        if (!user.isEmailVerified()) {
            if (authPasswordResetTokenRepository.existsByUserIdAndPurpose(user.getId(), PasswordLinkPurpose.INVITE)) {
                String restaurantName = user.getRestaurantId() == null ? null : restaurantRepository
                        .findByIdAndDeletedAtIsNull(user.getRestaurantId()).map(restaurant -> restaurant.getName()).orElse(null);
                sendInvite(user, request.getClientTarget(), restaurantName, now);
            }
            return;
        }

        // 4. Replace older links and send the new one
        OpaqueTokenService.IssuedToken issuedToken = replacePasswordResetToken(user, now);
        sendResetEmail(user, request.getClientTarget(), issuedToken, now);
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

        sendResetEmail(user, clientTarget, replacePasswordResetToken(user, now), now);
    }

    private void sendResetEmail(
            User user,
            ClientLinkTarget clientTarget,
            OpaqueTokenService.IssuedToken issuedToken,
            OffsetDateTime now
    ) {
        authMailService.sendPasswordResetEmail(
                user,
                passwordResetProperties.getSubject(),
                buildResetUrl(clientTarget, issuedToken),
                passwordResetProperties.getTokenTtl(),
                now.plus(passwordResetProperties.getTokenTtl())
        );
    }

    private void sendInvite(User user, ClientLinkTarget clientTarget, String restaurantName, OffsetDateTime now) {
        Duration ttl = passwordResetProperties.getInviteTokenTtl();
        OpaqueTokenService.IssuedToken issuedToken = issueLink(user, PasswordLinkPurpose.INVITE, ttl, now);
        authMailService.sendAccountSetupEmail(user, buildResetUrl(clientTarget, issuedToken), ttl, now.plus(ttl), restaurantName);
    }

    private OpaqueTokenService.IssuedToken replacePasswordResetToken(User user, OffsetDateTime now) {
        return issueLink(user, PasswordLinkPurpose.RESET, passwordResetProperties.getTokenTtl(), now);
    }

    // Only the newest link works; the older ones stay (marked replaced) so they can explain why.
    private OpaqueTokenService.IssuedToken issueLink(User user, PasswordLinkPurpose purpose, Duration ttl, OffsetDateTime now) {
        authPasswordResetTokenRepository.supersedeOpenLinks(user.getId(), now);

        OpaqueTokenService.IssuedToken issuedToken = opaqueTokenService.issue(passwordResetProperties.getTokenPepper());
        authPasswordResetTokenRepository.save(
                AuthPasswordResetToken.builder()
                        .userId(user.getId())
                        .tokenHash(issuedToken.tokenHash())
                        .purpose(purpose)
                        .expiresAt(now.plus(ttl))
                        .build()
        );

        return issuedToken;
    }

    // The page that takes the link (served by this backend unless a website takes over).
    private String buildResetUrl(ClientLinkTarget clientTarget, OpaqueTokenService.IssuedToken issuedToken) {
        String linkBaseUrl = passwordResetProperties.getLinkBaseUrl();
        return FrontendUrlUtils.buildTokenUrl(
                linkBaseUrl == null || linkBaseUrl.isBlank() ? frontendProperties.resolveBaseUrl(clientTarget) : linkBaseUrl.trim(),
                passwordResetProperties.getResetPath(),
                issuedToken.rawToken()
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
