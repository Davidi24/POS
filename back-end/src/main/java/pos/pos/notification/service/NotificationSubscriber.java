package pos.pos.notification.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pos.pos.notification.enums.NotificationTopic;

import java.util.Set;
import java.util.UUID;

record NotificationSubscriber(
        UUID id,
        UUID userId,
        UUID restaurantId,
        UUID branchId,
        Set<NotificationTopic> topics,
        Set<String> eventCodes,
        Set<String> disabledEventCodes,
        SseEmitter emitter
) {

    boolean accepts(NotificationOperationalEvent event) {
        if (!restaurantId.equals(event.restaurantId())) {
            return false;
        }
        if (branchId != null && event.branchId() != null && !branchId.equals(event.branchId())) {
            return false;
        }
        if (branchId != null && event.branchId() == null) {
            return false;
        }
        if (event.recipientUserId() != null && !userId.equals(event.recipientUserId())) {
            return false;
        }
        if (!topics.isEmpty() && !topics.contains(event.topic())) {
            return false;
        }
        if (!eventCodes.isEmpty() && !eventCodes.contains(event.eventCode())) {
            return false;
        }
        return !disabledEventCodes.contains(event.eventCode());
    }
}
