package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.repository.AuthEmailVerificationTokenRepository;
import pos.pos.auth.repository.AuthLoginAttemptRepository;
import pos.pos.auth.repository.AuthPasswordResetTokenRepository;
import pos.pos.auth.repository.AuthSmsOtpCodeRepository;
import pos.pos.auth.repository.UserSessionRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

//checked
@Service
@RequiredArgsConstructor
public class SessionCleanupService {

    private final UserSessionRepository userSessionRepository;
    private final AuthPasswordResetTokenRepository authPasswordResetTokenRepository;
    private final AuthEmailVerificationTokenRepository authEmailVerificationTokenRepository;
    private final AuthSmsOtpCodeRepository authSmsOtpCodeRepository;
    private final AuthLoginAttemptRepository authLoginAttemptRepository;

    private static final int PASSWORD_LINK_MEMORY_DAYS = 30;

    @Value("${app.security.login.attempt-retention-days:90}")
    private int attemptRetentionDays;

    // Runs every hour at minute 0 and second 0
    @Scheduled(cron = "0 0 * * * *") // every hour
    @Transactional
    public void cleanup() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // Deletes expired or revoked sessions every hour
        userSessionRepository.deleteExpiredOrRevokedSessions(now);
        // Password links are kept 30 days after they stop working, so an old link says "expired" or "already used"
        // instead of "unknown".
        authPasswordResetTokenRepository.deleteExpiredTokens(now.minusDays(PASSWORD_LINK_MEMORY_DAYS));
        authPasswordResetTokenRepository.deleteSpentTokens(now.minusDays(PASSWORD_LINK_MEMORY_DAYS));
        // Deletes expired email verification tokens that are no longer usable for account activation
        authEmailVerificationTokenRepository.deleteExpiredTokens(now);
        // Deletes expired SMS OTP codes used for password reset and phone verification.
        authSmsOtpCodeRepository.deleteExpiredCodes(now);
        // Deletes login attempt records older than the configured retention period (e.g. 90 days)
        authLoginAttemptRepository.deleteOlderThan(now.minusDays(attemptRetentionDays));
    }
}
