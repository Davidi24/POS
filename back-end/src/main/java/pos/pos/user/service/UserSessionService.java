package pos.pos.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pos.pos.user.entity.UserSession;
import pos.pos.user.repository.UserSessionRepository;
import com.github.f4b6a3.uuid.UuidCreator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final UserSessionRepository userSessionRepository;

    public UserSession createSession(UUID userId, String refreshTokenHash, String ipAddress, String userAgent, OffsetDateTime expiresAt) {

        UserSession session = UserSession.builder()
                .id(UuidCreator.getTimeOrdered())
                .userId(userId)
                .refreshTokenHash(refreshTokenHash)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .expiresAt(expiresAt)
                .createdAt(OffsetDateTime.now())
                .revoked(false)
                .build();

        return userSessionRepository.save(session);
    }

}