package pos.pos.notification.service;

import org.springframework.util.StringUtils;
import pos.pos.notification.enums.NotificationMutationType;
import pos.pos.notification.enums.NotificationTopic;
import pos.pos.utils.NormalizationUtils;

import java.util.Locale;

final class NotificationEventCodeSupport {

    private NotificationEventCodeSupport() {
    }

    static String normalizeEventCode(
            String requestedEventCode,
            NotificationTopic topic,
            NotificationMutationType mutationType,
            String referenceType
    ) {
        String normalized = NormalizationUtils.normalizeUpper(requestedEventCode);
        if (normalized != null) {
            return normalized;
        }

        String typeToken = toToken(referenceType);
        if (!StringUtils.hasText(typeToken)) {
            return topic.name() + "_" + mutationType.name();
        }
        return topic.name() + "_" + typeToken + "_" + mutationType.name();
    }

    static NotificationTopic topicOf(String eventCode) {
        String normalized = NormalizationUtils.normalizeUpper(eventCode);
        if (normalized == null) {
            return null;
        }

        for (NotificationTopic topic : NotificationTopic.values()) {
            String prefix = topic.name() + "_";
            if (normalized.equals(topic.name()) || normalized.startsWith(prefix)) {
                return topic;
            }
        }
        return null;
    }

    static String defaultSubject(NotificationTopic topic, NotificationMutationType mutationType, String referenceType) {
        String resource = humanize(referenceType == null ? topic.name() : referenceType);
        return switch (mutationType) {
            case DELETE -> resource + " deleted";
            case STATE_CHANGE -> resource + " status changed";
            case BROADCAST -> resource + " notification";
            default -> resource + " updated";
        };
    }

    static String defaultBody(NotificationTopic topic, NotificationMutationType mutationType, String referenceType) {
        String resource = humanize(referenceType == null ? topic.name() : referenceType);
        return switch (mutationType) {
            case DELETE -> resource + " was deleted";
            case STATE_CHANGE -> resource + " changed state";
            case BROADCAST -> resource + " broadcast";
            default -> resource + " changed";
        };
    }

    static String toToken(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }

        String value = rawValue.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private static String humanize(String rawValue) {
        String token = toToken(rawValue);
        if (!StringUtils.hasText(token)) {
            return "Notification";
        }
        String[] parts = token.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return builder.toString();
    }
}
