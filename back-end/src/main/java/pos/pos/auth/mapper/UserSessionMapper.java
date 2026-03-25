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
            String refreshTokenHash,
            ClientInfo clientInfo
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        String userAgent = normalizeUserAgent(clientInfo != null ? clientInfo.userAgent() : null);
        String finalDeviceName = extractDeviceName(userAgent);

        return UserSession.builder()
                .userId(userId)
                .tokenId(tokenId)
                .sessionType(sessionType)
                .deviceName(finalDeviceName)
                .refreshTokenHash(refreshTokenHash)
                .ipAddress(normalizeIp(clientInfo != null ? clientInfo.ipAddress() : null))
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

    private String normalizeUserAgent(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return null;
        }
        return userAgent.trim();
    }

    private String normalizeIp(String ip) {
        if (ip == null || ip.isBlank()) {
            return null;
        }
        return ip.trim();
    }
}