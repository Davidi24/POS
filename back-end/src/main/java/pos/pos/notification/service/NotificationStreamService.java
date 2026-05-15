package pos.pos.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pos.pos.exception.auth.AuthException;
import pos.pos.notification.dto.NotificationLiveEventResponse;
import pos.pos.notification.entity.Notification;
import pos.pos.notification.enums.NotificationChannel;
import pos.pos.notification.enums.NotificationTopic;
import pos.pos.notification.repository.NotificationPreferenceRepository;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.utils.NormalizationUtils;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationStreamService {

    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final NotificationMapper notificationMapper;
    private final RestaurantScopeService restaurantScopeService;
    private final ActorScopeService actorScopeService;

    private final ConcurrentMap<UUID, NotificationSubscriber> subscribers = new ConcurrentHashMap<>();

    public SseEmitter subscribe(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            Set<NotificationTopic> topics,
            Set<String> eventCodes
    ) {
        if (branchId == null) {
            restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        } else {
            restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        }

        UUID userId = actorScopeService.currentUserId(authentication);
        Set<String> disabledCodes = notificationPreferenceRepository
                .findAllByUser_IdAndChannelAndEnabledFalse(userId, NotificationChannel.IN_APP)
                .stream()
                .map(preference -> preference.getEventCode())
                .collect(Collectors.toSet());

        SseEmitter emitter = new SseEmitter(0L);
        UUID subscriberId = UUID.randomUUID();
        NotificationSubscriber subscriber = new NotificationSubscriber(
                subscriberId,
                userId,
                restaurantId,
                branchId,
                topics,
                eventCodes.stream()
                        .map(NormalizationUtils::normalizeUpper)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet()),
                disabledCodes,
                emitter
        );

        subscribers.put(subscriberId, subscriber);
        emitter.onCompletion(() -> subscribers.remove(subscriberId));
        emitter.onTimeout(() -> subscribers.remove(subscriberId));
        emitter.onError(ex -> subscribers.remove(subscriberId));

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .id(subscriberId.toString())
                    .data("connected"));
        } catch (IOException ex) {
            subscribers.remove(subscriberId);
            throw new AuthException("Unable to open notification stream", HttpStatus.SERVICE_UNAVAILABLE);
        }

        return emitter;
    }

    void broadcast(NotificationOperationalEvent event, Notification notification, OffsetDateTime occurredAt) {
        NotificationLiveEventResponse payload = notificationMapper.toLiveEvent(notification, event, occurredAt);
        for (NotificationSubscriber subscriber : subscribers.values()) {
            if (!subscriber.accepts(event)) {
                continue;
            }
            try {
                subscriber.emitter().send(SseEmitter.event()
                        .name("notification")
                        .id(payload.getStreamEventId())
                        .data(payload));
            } catch (IOException ex) {
                subscribers.remove(subscriber.id());
                subscriber.emitter().completeWithError(ex);
            }
        }
    }
}
