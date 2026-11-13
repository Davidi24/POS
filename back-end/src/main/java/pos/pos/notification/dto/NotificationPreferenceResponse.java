package pos.pos.notification.dto;

import lombok.Builder;
import lombok.Getter;
import pos.pos.notification.enums.NotificationChannel;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class NotificationPreferenceResponse {

    private UUID id;
    private UUID userId;
    private NotificationChannel channel;
    private String eventCode;
    private boolean enabled;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
