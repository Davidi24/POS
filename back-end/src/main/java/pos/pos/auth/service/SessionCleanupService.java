package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.repository.AuthEmailVerificationTokenRepository;
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

    @Scheduled(cron = "0 0 * * * *") // every hour
    @Transactional
    public void cleanup() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        userSessionRepository.deleteExpiredOrRevokedSessions(now);
        authPasswordResetTokenRepository.deleteExpiredTokens(now);
        authEmailVerificationTokenRepository.deleteExpiredTokens(now);
    }
}
