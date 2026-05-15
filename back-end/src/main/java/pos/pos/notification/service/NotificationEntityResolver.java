package pos.pos.notification.service;

import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import pos.pos.notification.entity.Notification;
import pos.pos.notification.entity.NotificationPreference;
import pos.pos.notification.entity.NotificationTemplate;
import pos.pos.notification.enums.NotificationChannel;
import pos.pos.notification.enums.NotificationMutationType;
import pos.pos.notification.enums.NotificationPriority;
import pos.pos.notification.enums.NotificationTopic;

import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
class NotificationEntityResolver {

    private static final Set<String> SKIPPED_PACKAGES = Set.of(
            "notification",
            "audit",
            "auth"
    );

    Optional<NotificationOperationalEvent> resolveEntityChange(Object entity, NotificationMutationType mutationType) {
        if (entity == null) {
            return Optional.empty();
        }

        if (entity instanceof Notification || entity instanceof NotificationTemplate || entity instanceof NotificationPreference) {
            return Optional.empty();
        }

        Class<?> entityClass = ClassUtils.getUserClass(entity);
        NotificationTopic topic = resolveTopic(entityClass);
        if (topic == null) {
            return Optional.empty();
        }

        ResolvedContext context = resolveContext(entity, new IdentityHashMap<>());
        if (context.restaurantId() == null) {
            return Optional.empty();
        }

        UUID referenceId = asUuid(readProperty(entity, "getId"));
        String referenceType = NotificationEventCodeSupport.toToken(entityClass.getSimpleName());
        NotificationPriority priority = defaultPriority(topic);
        String eventCode = NotificationEventCodeSupport.normalizeEventCode(null, topic, mutationType, referenceType);

        UUID branchId = context.branchId();
        if (branchId == null && topic == NotificationTopic.BRANCH) {
            branchId = referenceId;
        }

        UUID restaurantId = context.restaurantId();
        if (restaurantId == null && topic == NotificationTopic.RESTAURANT) {
            restaurantId = referenceId;
        }

        return Optional.of(new NotificationOperationalEvent(
                topic,
                mutationType,
                NotificationChannel.IN_APP,
                priority,
                eventCode,
                restaurantId,
                branchId,
                null,
                referenceType,
                referenceId,
                context.actorId(),
                null,
                null
        ));
    }

    private NotificationTopic resolveTopic(Class<?> entityClass) {
        String packageName = entityClass.getPackageName();
        String prefix = "pos.pos.";
        if (!packageName.startsWith(prefix)) {
            return null;
        }

        String[] segments = packageName.substring(prefix.length()).split("\\.");
        if (segments.length == 0 || SKIPPED_PACKAGES.contains(segments[0])) {
            return null;
        }

        try {
            return NotificationTopic.valueOf(segments[0].toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private ResolvedContext resolveContext(Object entity, Map<Object, Boolean> visited) {
        if (entity == null || visited.put(entity, Boolean.TRUE) != null) {
            return ResolvedContext.empty();
        }

        UUID restaurantId = asUuid(readProperty(entity, "getRestaurantId"));
        UUID branchId = asUuid(readProperty(entity, "getBranchId"));
        UUID actorId = asUuid(readProperty(entity, "getUpdatedBy"));
        if (actorId == null) {
            actorId = asUuid(readProperty(entity, "getCreatedBy"));
        }

        Object restaurant = readProperty(entity, "getRestaurant");
        if (restaurant != null) {
            restaurantId = firstNonNull(restaurantId, asUuid(readProperty(restaurant, "getId")));
        }

        Object branch = readProperty(entity, "getBranch");
        if (branch != null) {
            branchId = firstNonNull(branchId, asUuid(readProperty(branch, "getId")));
            if (restaurantId == null) {
                restaurantId = asUuid(readProperty(readProperty(branch, "getRestaurant"), "getId"));
            }
        }

        if (restaurantId != null) {
            return new ResolvedContext(restaurantId, branchId, actorId);
        }

        for (String nestedGetter : new String[]{"getOrder", "getDevice", "getMenu", "getStation", "getReservation", "getUser"}) {
            Object nested = readProperty(entity, nestedGetter);
            if (nested == null) {
                continue;
            }
            ResolvedContext nestedContext = resolveContext(nested, visited);
            restaurantId = firstNonNull(restaurantId, nestedContext.restaurantId());
            branchId = firstNonNull(branchId, nestedContext.branchId());
            actorId = firstNonNull(actorId, nestedContext.actorId());
            if (restaurantId != null) {
                return new ResolvedContext(restaurantId, branchId, actorId);
            }
        }

        return new ResolvedContext(restaurantId, branchId, actorId);
    }

    private Object readProperty(Object source, String methodName) {
        if (source == null) {
            return null;
        }

        Method method = ReflectionUtils.findMethod(ClassUtils.getUserClass(source), methodName);
        if (method == null) {
            return null;
        }
        ReflectionUtils.makeAccessible(method);
        return ReflectionUtils.invokeMethod(method, source);
    }

    private UUID asUuid(Object value) {
        return value instanceof UUID uuid ? uuid : null;
    }

    private NotificationPriority defaultPriority(NotificationTopic topic) {
        return switch (topic) {
            case ORDER, KDS, TABLE, RESERVATION, INVENTORY -> NotificationPriority.HIGH;
            case PAYMENT -> NotificationPriority.CRITICAL;
            default -> NotificationPriority.NORMAL;
        };
    }

    private UUID firstNonNull(UUID current, UUID fallback) {
        return current != null ? current : fallback;
    }

    private record ResolvedContext(UUID restaurantId, UUID branchId, UUID actorId) {
        private static ResolvedContext empty() {
            return new ResolvedContext(null, null, null);
        }
    }
}
