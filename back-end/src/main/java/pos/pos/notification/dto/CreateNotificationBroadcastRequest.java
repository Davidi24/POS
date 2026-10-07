package pos.pos.notification.dto;

import jakarta.validation.constraints.Size;
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

    @Size(max = 80, message = "eventCode must be at most 80 characters")
    private String eventCode;

    @Size(max = 150, message = "subject must be at most 150 characters")
    private String subject;

    @NotBlank
    @Size(max = 5000, message = "body must be at most 5000 characters")
    private String body;

    @Size(max = 50, message = "referenceType must be at most 50 characters")
    private String referenceType;

    private UUID referenceId;
}
