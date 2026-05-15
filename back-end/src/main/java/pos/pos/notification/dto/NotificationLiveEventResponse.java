package pos.pos.notification.dto;

import lombok.Builder;
import lombok.Getter;
import pos.pos.notification.enums.NotificationMutationType;
import pos.pos.notification.enums.NotificationPriority;
import pos.pos.notification.enums.NotificationTopic;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class NotificationLiveEventResponse {

    private String streamEventId;
    private UUID notificationId;
    private NotificationTopic topic;
    private NotificationMutationType mutationType;
    private NotificationPriority priority;
    private String eventCode;
    private UUID restaurantId;
    private UUID branchId;
    private UUID recipientUserId;
    private String referenceType;
    private UUID referenceId;
    private String subject;
    private String body;
    private OffsetDateTime occurredAt;
}
