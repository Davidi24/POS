package pos.pos.notification.dto;

import lombok.Builder;
import lombok.Getter;
import pos.pos.notification.enums.NotificationChannel;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class NotificationTemplateResponse {

    private UUID id;
    private UUID restaurantId;
    private String code;
    private String name;
    private NotificationChannel channel;
    private String subjectTemplate;
    private String bodyTemplate;
    private boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
}
