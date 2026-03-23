package pos.pos.auth.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pos.pos.security.config.JwtProperties;
import pos.pos.auth.entity.UserSession;
import pos.pos.security.util.ClientInfo;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UserSessionMapper {

    private final JwtProperties jwtProperties;

    public UserSession toSession(
            UUID userId,
            UUID tokenId,
            String sessionType,
            String deviceName,
            String refreshTokenHash,
            ClientInfo clientInfo
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        return UserSession.builder()
                .userId(userId)
                .tokenId(tokenId)
                .sessionType(sessionType)
                .deviceName(deviceName)
                .refreshTokenHash(refreshTokenHash)
                .ipAddress(clientInfo != null ? clientInfo.ipAddress() : null)
                .userAgent(clientInfo != null ? clientInfo.userAgent() : null)
                .lastUsedAt(now)
                .expiresAt(now.plusSeconds(jwtProperties.getRefreshExpiration().getSeconds()))
                .createdAt(now)
                .revoked(false)
                .build();
    }
}
