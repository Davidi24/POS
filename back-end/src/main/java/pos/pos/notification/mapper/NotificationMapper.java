package pos.pos.notification.mapper;

import org.springframework.stereotype.Component;
import pos.pos.notification.dto.NotificationLiveEventResponse;
import pos.pos.notification.dto.NotificationPreferenceResponse;
import pos.pos.notification.dto.NotificationResponse;
import pos.pos.notification.dto.NotificationTemplateResponse;
import pos.pos.notification.entity.Notification;
import pos.pos.notification.entity.NotificationPreference;
import pos.pos.notification.entity.NotificationTemplate;
import pos.pos.notification.enums.NotificationMutationType;
import pos.pos.notification.enums.NotificationPriority;
import pos.pos.notification.enums.NotificationTopic;
import pos.pos.notification.support.NotificationEventCodeSupport;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification, UUID currentUserId) {
        boolean personal = notification.getRecipientUser() != null
                && currentUserId != null
                && currentUserId.equals(notification.getRecipientUser().getId());
        NotificationTopic topic = NotificationEventCodeSupport.topicOf(notification.getEventCode());

        return NotificationResponse.builder()
                .id(notification.getId())
                .restaurantId(notification.getRestaurant() == null ? null : notification.getRestaurant().getId())
                .branchId(notification.getBranch() == null ? null : notification.getBranch().getId())
                .recipientUserId(notification.getRecipientUser() == null ? null : notification.getRecipientUser().getId())
                .topic(topic)
                .channel(notification.getChannel())
                .status(notification.getStatus())
                .priority(notification.getPriority())
                .eventCode(notification.getEventCode())
                .subject(notification.getSubject())
                .body(notification.getBody())
                .referenceType(notification.getReferenceType())
                .referenceId(notification.getReferenceId())
                .scheduledAt(notification.getScheduledAt())
                .sentAt(notification.getSentAt())
                .deliveredAt(notification.getDeliveredAt())
                .readAt(notification.getReadAt())
                .failureReason(notification.getFailureReason())
                .attemptCount(notification.getAttemptCount())
                .createdAt(notification.getCreatedAt())
                .updatedAt(notification.getUpdatedAt())
                .createdBy(notification.getCreatedBy())
                .updatedBy(notification.getUpdatedBy())
                .personal(personal)
                .markReadAllowed(personal)
                .build();
    }

    public NotificationPreferenceResponse toResponse(NotificationPreference preference) {
        return NotificationPreferenceResponse.builder()
                .id(preference.getId())
                .userId(preference.getUser() == null ? null : preference.getUser().getId())
                .channel(preference.getChannel())
                .eventCode(preference.getEventCode())
                .enabled(preference.isEnabled())
                .createdAt(preference.getCreatedAt())
                .updatedAt(preference.getUpdatedAt())
                .build();
    }

    public NotificationTemplateResponse toResponse(NotificationTemplate template) {
        return NotificationTemplateResponse.builder()
                .id(template.getId())
                .restaurantId(template.getRestaurant() == null ? null : template.getRestaurant().getId())
                .code(template.getCode())
                .name(template.getName())
                .channel(template.getChannel())
                .subjectTemplate(template.getSubjectTemplate())
                .bodyTemplate(template.getBodyTemplate())
                .active(template.isActive())
                .createdAt(template.getCreatedAt())
                .updatedAt(template.getUpdatedAt())
                .createdBy(template.getCreatedBy())
                .updatedBy(template.getUpdatedBy())
                .build();
    }

    public NotificationLiveEventResponse toLiveEvent(
            Notification notification,
            NotificationTopic topic,
            NotificationMutationType mutationType,
            NotificationPriority priority,
            OffsetDateTime occurredAt
    ) {
        return NotificationLiveEventResponse.builder()
                .streamEventId(notification.getId().toString())
                .notificationId(notification.getId())
                .topic(topic)
                .mutationType(mutationType)
                .priority(priority)
                .eventCode(notification.getEventCode())
                .restaurantId(notification.getRestaurant() == null ? null : notification.getRestaurant().getId())
                .branchId(notification.getBranch() == null ? null : notification.getBranch().getId())
                .recipientUserId(notification.getRecipientUser() == null ? null : notification.getRecipientUser().getId())
                .referenceType(notification.getReferenceType())
                .referenceId(notification.getReferenceId())
                .subject(notification.getSubject())
                .body(notification.getBody())
                .occurredAt(occurredAt)
                .build();
    }
}
