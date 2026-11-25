package pos.pos.user.dto;

import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSessionResponse {

    private UUID id;
    private UUID userId;
    private String sessionType;
    private String deviceName;
    // What it runs ("Linux 6.17") and where its address is ("Tirana, Albania"); on this device's network (2026-11-25).
    private String deviceSystem;
    private String place;
    private boolean sameNetwork;
    private String ipAddress;
    private String userAgent;
    private OffsetDateTime lastUsedAt;
    private OffsetDateTime expiresAt;
    private OffsetDateTime createdAt;
    private boolean current;

}