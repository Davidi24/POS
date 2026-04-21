package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.dto.ChangePasswordRequest;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.exception.auth.InvalidCredentialsException;
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

    @Transactional
    public void changePassword(UUID userId, UUID currentTokenId, ChangePasswordRequest request) {
        User user = userRepository.findActiveById(userId)
                .orElseThrow(UserNotFoundException::new);

        if (!passwordService.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
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

        authMailService.sendPasswordChangedNotificationEmail(user.getEmail(), user.getFirstName());
    }
}
