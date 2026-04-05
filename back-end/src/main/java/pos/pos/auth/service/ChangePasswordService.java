package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.dto.ChangePasswordRequest;
import pos.pos.auth.entity.UserSession;
import pos.pos.auth.enums.SessionRevocationReason;
import pos.pos.auth.repository.UserSessionRepository;
import pos.pos.exception.auth.InvalidCredentialsException;
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

    @Transactional
    public void changePassword(User user, UUID currentTokenId, ChangePasswordRequest request) {
        if (!passwordService.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        user.setPasswordHash(passwordService.hash(request.getNewPassword()));
        user.setPasswordUpdatedAt(now);
        userRepository.save(user);

        String reason = SessionRevocationReason.PASSWORD_CHANGED.name();
        userSessionRepository.findByTokenIdAndRevokedFalse(currentTokenId)
                .map(UserSession::getId)
                .ifPresentOrElse(
                        currentSessionId -> userSessionRepository.revokeAllActiveSessionsByUserIdExcept(
                                user.getId(), currentSessionId, now, reason),
                        () -> userSessionRepository.revokeAllActiveSessionsByUserId(
                                user.getId(), now, reason)
                );
    }
}