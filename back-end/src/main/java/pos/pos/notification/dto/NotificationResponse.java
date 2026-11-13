package pos.pos.notification.dto;

import lombok.Builder;
import lombok.Getter;
import pos.pos.notification.enums.NotificationChannel;
import pos.pos.notification.enums.NotificationPriority;
import pos.pos.notification.enums.NotificationStatus;
import pos.pos.notification.enums.NotificationTopic;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Builder
public class NotificationResponse {

    private UUID id;
    private UUID restaurantId;
    private UUID branchId;
    private UUID recipientUserId;
    private NotificationTopic topic;
    private NotificationChannel channel;
    private NotificationStatus status;
    private NotificationPriority priority;
    private String eventCode;
    private String subject;
    private String body;
    private String referenceType;
    private UUID referenceId;
    private OffsetDateTime scheduledAt;
    private OffsetDateTime sentAt;
    private OffsetDateTime deliveredAt;
    private OffsetDateTime readAt;
    private String failureReason;
    private int attemptCount;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    private boolean personal;
    private boolean markReadAllowed;
}
