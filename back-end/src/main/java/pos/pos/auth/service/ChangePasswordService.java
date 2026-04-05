package pos.pos.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.auth.dto.ChangePasswordRequest;
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
    private final UserSessionRepository userSessionRepository;
    private final PasswordService passwordService;

    @Transactional
    public void changePassword(User user, UUID tokenId, ChangePasswordRequest request) {
        if (!passwordService.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        user.setPasswordHash(passwordService.hash(request.getNewPassword()));
        user.setPasswordUpdatedAt(now);
        userRepository.save(user);

        userSessionRepository.findByTokenIdAndRevokedFalse(tokenId).ifPresent(currentSession ->
                userSessionRepository.revokeAllActiveSessionsByUserIdExcept(
                        user.getId(),
                        currentSession.getId(),
                        now,
                        SessionRevocationReason.PASSWORD_RESET.name()
                )
        );
    }
}