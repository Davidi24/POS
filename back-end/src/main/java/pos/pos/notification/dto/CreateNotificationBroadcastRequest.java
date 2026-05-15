package pos.pos.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pos.pos.notification.enums.NotificationChannel;
import pos.pos.notification.enums.NotificationPriority;
import pos.pos.notification.enums.NotificationTopic;

import java.util.UUID;

@Getter
@Setter
public class CreateNotificationBroadcastRequest {

    private UUID branchId;

    private UUID recipientUserId;

    @NotNull
    private NotificationTopic topic;

    private NotificationChannel channel = NotificationChannel.IN_APP;

    private NotificationPriority priority = NotificationPriority.NORMAL;

    private String eventCode;

    private String subject;

    @NotBlank
    private String body;

    private String referenceType;

    private UUID referenceId;
}
