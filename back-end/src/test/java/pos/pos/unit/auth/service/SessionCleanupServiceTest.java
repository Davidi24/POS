package pos.pos.unit.auth.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.auth.repository.AuthEmailVerificationTokenRepository;
import pos.pos.auth.repository.AuthLoginAttemptRepository;
import pos.pos.auth.repository.AuthPasswordResetTokenRepository;
import pos.pos.auth.repository.AuthSmsOtpCodeRepository;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.auth.service.SessionCleanupService;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SessionCleanupServiceTest {

    @Mock
    private UserSessionRepository userSessionRepository;

    @Mock
    private AuthPasswordResetTokenRepository authPasswordResetTokenRepository;

    @Mock
    private AuthEmailVerificationTokenRepository authEmailVerificationTokenRepository;

    @Mock
    private AuthSmsOtpCodeRepository authSmsOtpCodeRepository;

    @Mock
    private AuthLoginAttemptRepository authLoginAttemptRepository;

    @InjectMocks
    private SessionCleanupService sessionCleanupService;

    @Test
    void cleanup_shouldDeleteExpiredSessionsAndAuthTokens() {
        sessionCleanupService.cleanup();

        verify(userSessionRepository).deleteExpiredOrRevokedSessions(any());
        verify(authPasswordResetTokenRepository).deleteExpiredTokens(any());
        verify(authEmailVerificationTokenRepository).deleteExpiredTokens(any());
        verify(authSmsOtpCodeRepository).deleteExpiredCodes(any());
        verify(authLoginAttemptRepository).deleteOlderThan(any());
    }
}
