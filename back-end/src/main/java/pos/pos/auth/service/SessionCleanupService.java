package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.repository.AuthEmailVerificationTokenRepository;
import pos.pos.auth.repository.AuthLoginAttemptRepository;
import pos.pos.auth.repository.AuthPasswordResetTokenRepository;
import pos.pos.auth.repository.UserSessionRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class SessionCleanupService {

    private final UserSessionRepository userSessionRepository;
    private final AuthPasswordResetTokenRepository authPasswordResetTokenRepository;
    private final AuthEmailVerificationTokenRepository authEmailVerificationTokenRepository;
    private final AuthLoginAttemptRepository authLoginAttemptRepository;

    @Value("${app.security.login.attempt-retention-days:90}")
    private int attemptRetentionDays;

    // Runs every hour at minute 0 and second 0
    @Scheduled(cron = "0 0 * * * *") // every hour
    @Transactional
    public void cleanup() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // Deletes expired or revoked sessions every hour
        userSessionRepository.deleteExpiredOrRevokedSessions(now);
        // Removes expired password reset tokens that are no longer valid for resetting passwords
        authPasswordResetTokenRepository.deleteExpiredTokens(now);
        // Deletes expired email verification tokens that are no longer usable for account activation
        authEmailVerificationTokenRepository.deleteExpiredTokens(now);
        // Deletes login attempt records older than the configured retention period (e.g. 90 days)
        authLoginAttemptRepository.deleteOlderThan(now.minusDays(attemptRetentionDays));
    }
}
