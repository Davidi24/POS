package pos.pos.user.mapper;

import pos.pos.user.entity.UserSession;
import pos.pos.user.dto.UserSessionResponse;

public class UserSessionMapper {

    public static UserSessionResponse toResponse(UserSession session) {
        if (session == null) return null;

        return UserSessionResponse.builder()
                .id(session.getId())
                .userId(session.getUserId())
                .ipAddress(session.getIpAddress())
                .userAgent(session.getUserAgent())
                .lastUsedAt(session.getLastUsedAt())
                .expiresAt(session.getExpiresAt())
                .revoked(session.getRevoked())
                .build();
    }

}