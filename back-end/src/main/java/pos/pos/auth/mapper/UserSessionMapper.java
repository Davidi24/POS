package pos.pos.auth.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pos.pos.security.config.JwtProperties;
import pos.pos.auth.entity.UserSession;
import pos.pos.security.util.ClientInfo;
import pos.pos.security.util.ClientInfoNormalizer;

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
            String refreshTokenHash,
            ClientInfo clientInfo
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ClientInfo normalizedClientInfo = ClientInfoNormalizer.normalize(clientInfo);

        String userAgent = normalizedClientInfo != null ? normalizedClientInfo.userAgent() : null;
        String finalDeviceName = extractDeviceName(userAgent);

        return UserSession.builder()
                .userId(userId)
                .tokenId(tokenId)
                .sessionType(sessionType)
                .deviceName(finalDeviceName)
                .refreshTokenHash(refreshTokenHash)
                .ipAddress(normalizedClientInfo != null ? normalizedClientInfo.ipAddress() : null)
                .userAgent(userAgent)
                .lastUsedAt(now)
                .expiresAt(now.plusSeconds(jwtProperties.getRefreshExpiration().getSeconds()))
                .createdAt(now)
                .revoked(false)
                .build();
    }

    private String extractDeviceName(String userAgent) {
        if (userAgent == null) {
            return "UNKNOWN";
        }

        if (userAgent.contains("Android")) return "Android";
        if (userAgent.contains("iPhone")) return "iPhone";
        if (userAgent.contains("Windows")) return "Windows";
        if (userAgent.contains("Mac")) return "Mac";
        if (userAgent.contains("Linux")) return "Linux";

        return "Device";
    }
}
