package pos.pos.notification.service;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pos.pos.notification.entity.NotificationTemplate;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Component
class NotificationTemplateRenderer {

    RenderedNotification render(NotificationOperationalEvent event, NotificationTemplate template, OffsetDateTime happenedAt) {
        String defaultSubject = NotificationEventCodeSupport.defaultSubject(
                event.topic(),
                event.mutationType(),
                event.referenceType()
        );
        String defaultBody = StringUtils.hasText(event.body())
                ? event.body()
                : NotificationEventCodeSupport.defaultBody(event.topic(), event.mutationType(), event.referenceType());

        if (template == null) {
            return new RenderedNotification(
                    StringUtils.hasText(event.subject()) ? event.subject() : defaultSubject,
                    defaultBody
            );
        }

        Map<String, String> replacements = Map.of(
                "eventCode", event.eventCode(),
                "topic", event.topic().name(),
                "mutationType", event.mutationType().name(),
                "referenceType", nullSafe(event.referenceType()),
                "referenceId", nullSafe(uuidText(event.referenceId())),
                "restaurantId", nullSafe(uuidText(event.restaurantId())),
                "branchId", nullSafe(uuidText(event.branchId())),
                "recipientUserId", nullSafe(uuidText(event.recipientUserId())),
                "occurredAt", happenedAt.toString()
        );

        String subject = renderTemplate(
                StringUtils.hasText(template.getSubjectTemplate()) ? template.getSubjectTemplate() : defaultSubject,
                replacements
        );
        String body = renderTemplate(
                StringUtils.hasText(template.getBodyTemplate()) ? template.getBodyTemplate() : defaultBody,
                replacements
        );
        return new RenderedNotification(subject, body);
    }

    private String renderTemplate(String template, Map<String, String> replacements) {
        String rendered = template;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return rendered;
    }

    private String uuidText(UUID value) {
        return value == null ? null : value.toString();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    record RenderedNotification(String subject, String body) {
    }
}
