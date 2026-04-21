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
    private String ipAddress;
    private String userAgent;
    private OffsetDateTime lastUsedAt;
    private OffsetDateTime expiresAt;
    private OffsetDateTime createdAt;
    private boolean current;

}