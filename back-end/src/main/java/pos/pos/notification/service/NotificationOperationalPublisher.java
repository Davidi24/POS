package pos.pos.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pos.pos.notification.enums.NotificationChannel;
import pos.pos.notification.enums.NotificationMutationType;
import pos.pos.notification.enums.NotificationPriority;
import pos.pos.notification.enums.NotificationTopic;

import java.util.List;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationOperationalPublisher {

    private final NotificationDispatchService notificationDispatchService;
    private final NotificationEntityResolver notificationEntityResolver;

    private final ThreadLocal<Map<String, NotificationOperationalEvent>> queuedEvents =
            ThreadLocal.withInitial(LinkedHashMap::new);
    private final ThreadLocal<Boolean> synchronizationRegistered =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    public void publishEntityChange(Object entity, NotificationMutationType mutationType) {
        notificationEntityResolver.resolveEntityChange(entity, mutationType).ifPresent(this::queue);
    }

    public void publishCustom(
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
        if (restaurantId == null) {
            return;
        }

        NotificationOperationalEvent event = new NotificationOperationalEvent(
                topic,
                mutationType,
                channel == null ? NotificationChannel.IN_APP : channel,
                priority == null ? NotificationPriority.NORMAL : priority,
                NotificationEventCodeSupport.normalizeEventCode(eventCode, topic, mutationType, referenceType),
                restaurantId,
                branchId,
                recipientUserId,
                NotificationEventCodeSupport.toToken(referenceType),
                referenceId,
                actorId,
                subject,
                body
        );
        queue(event);
    }

    private void queue(NotificationOperationalEvent event) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            queuedEvents.get().put(event.dedupeKey(), event);
            registerAfterCommitIfNeeded();
            return;
        }

        notificationDispatchService.dispatch(List.of(event));
    }

    private void registerAfterCommitIfNeeded() {
        if (Boolean.TRUE.equals(synchronizationRegistered.get())) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                Collection<NotificationOperationalEvent> events = List.copyOf(queuedEvents.get().values());
                clear();
                notificationDispatchService.dispatch(events);
            }

            @Override
            public void afterCompletion(int status) {
                clear();
            }

            private void clear() {
                queuedEvents.remove();
                synchronizationRegistered.remove();
            }
        });

        synchronizationRegistered.set(Boolean.TRUE);
    }
}
