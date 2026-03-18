package pos.pos.auth.mapper;

import org.springframework.stereotype.Component;
import pos.pos.user.entity.UserSession;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class UserSessionMapper {

    public UserSession toSession(
            UUID userId,
            String refreshTokenHash,
            String ipAddress,
            String userAgent
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        return UserSession.builder()
                .userId(userId)
                .refreshTokenHash(refreshTokenHash)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .lastUsedAt(now)
                .expiresAt(now.plusDays(30))
                .createdAt(now)
                .revoked(false)
                .build();
    }
}