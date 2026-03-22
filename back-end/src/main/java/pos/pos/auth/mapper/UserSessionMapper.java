package pos.pos.auth.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pos.pos.security.config.JwtProperties;
import pos.pos.auth.entity.UserSession;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserSessionMapper {

    private final JwtProperties jwtProperties;

    public UserSession toSession(
            UUID userId,
            UUID tokenId,
            String refreshTokenHash,
            String ipAddress,
            String userAgent
    ) {
        OffsetDateTime now = OffsetDateTime.now();

        return UserSession.builder()
                .userId(userId)
                .tokenId(tokenId)
                .refreshTokenHash(refreshTokenHash)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .lastUsedAt(now)
                .expiresAt(now.plusSeconds(jwtProperties.getRefreshExpiration().getSeconds()))
                .createdAt(now)
                .revoked(false)
                .build();
    }
}