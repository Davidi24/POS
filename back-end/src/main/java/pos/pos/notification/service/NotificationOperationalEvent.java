package pos.pos.notification.service;

import pos.pos.notification.enums.NotificationChannel;
import pos.pos.notification.enums.NotificationMutationType;
import pos.pos.notification.enums.NotificationPriority;
import pos.pos.notification.enums.NotificationTopic;

import java.util.UUID;

record NotificationOperationalEvent(
        NotificationTopic topic,
        NotificationMutationType mutationType,
        NotificationChannel channel,
        NotificationPriority priority,
        String eventCode,
        UUID restaurantId,
        UUID branchId,
        UUID recipientUserId,
        String referenceType,
        UUID referenceId,
        UUID actorId,
        String subject,
        String body
) {

    String dedupeKey() {
        return topic + "|" + mutationType + "|" + channel + "|" + eventCode + "|" + restaurantId + "|" + branchId + "|"
                + recipientUserId + "|" + referenceType + "|" + referenceId + "|" + actorId;
    }
}
