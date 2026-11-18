package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.dto.ChangePasswordRequest;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.auth.InvalidCredentialsException;
import pos.pos.exception.auth.TooManyRequestsException;
import pos.pos.exception.user.UserNotFoundException;
import pos.pos.security.service.PasswordService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChangePasswordService {

    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final UserSessionRepository userSessionRepository;
    private final AuthMailService authMailService;
    private final PasswordDailyLimits passwordDailyLimits;
    private final AccountAudit accountAudit;

    // The limit's refusal keeps its "changed many times today" email (user, 2026-11-17).
    @Transactional(noRollbackFor = TooManyRequestsException.class)
    public void changePassword(UUID userId, UUID currentTokenId, ChangePasswordRequest request) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(UserNotFoundException::new);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        // At most 30 changes a day; the first refusal of the day emails the person.
        try {
            passwordDailyLimits.checkChange(user, now);
        } catch (TooManyRequestsException limit) {
            accountAudit.about(user, user.getId(), "PASSWORD_LIMIT", AccountAudit.nameOf(user) + " tried to change their password past today's limit");
            throw limit;
        }

        if (!passwordService.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }
        // The same rules as a password chosen from an emailed link, and it must be new.
        String problem = PasswordRules.problem(request.getNewPassword());
        if (problem != null) {
            throw new AuthException(problem, HttpStatus.BAD_REQUEST);
        }
        if (passwordService.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new AuthException("Choose a password different from your current one.", HttpStatus.BAD_REQUEST);
        }

        user.setPasswordHash(passwordService.hash(request.getNewPassword()));
        user.setPasswordUpdatedAt(now);
        userRepository.save(user);

        String reason = SessionRevocationReason.PASSWORD_CHANGED.name();

        //   Two cases:
        //  - Current session found → revoke all sessions except this one
        //  - Current session not found → revoke all sessions (no session to keep)
        userSessionRepository.findByTokenIdAndRevokedFalse(currentTokenId)
                .map(UserSession::getId)
                .ifPresentOrElse(
                        currentSessionId -> userSessionRepository.revokeAllActiveSessionsByUserIdExcept(
                                user.getId(), currentSessionId, now, reason),
                        () -> userSessionRepository.revokeAllActiveSessionsByUserId(
                                user.getId(), now, reason)
                );

        passwordDailyLimits.changed(user, now);
        authMailService.sendPasswordChangedNotificationEmail(user.getEmail(), user.getFirstName());
        accountAudit.about(user, user.getId(), "PASSWORD_CHANGED", AccountAudit.nameOf(user) + " changed their password; their other devices were signed out");
    }
}
