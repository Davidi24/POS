package pos.pos.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import pos.pos.notification.dto.NotificationResponse;
import pos.pos.notification.entity.Notification;
import pos.pos.notification.entity.NotificationTemplate;
import pos.pos.notification.enums.NotificationChannel;
import pos.pos.notification.enums.NotificationStatus;
import pos.pos.notification.repository.NotificationRepository;
import pos.pos.notification.repository.NotificationTemplateRepository;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.user.repository.UserRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
class NotificationDispatchService {

    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRepository notificationTemplateRepository;
    private final RestaurantRepository restaurantRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;
    private final NotificationTemplateRenderer notificationTemplateRenderer;
    private final NotificationStreamService notificationStreamService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatch(Collection<NotificationOperationalEvent> events) {
        if (events.isEmpty()) {
            return;
        }

        OffsetDateTime happenedAt = OffsetDateTime.now(ZoneOffset.UTC);
        List<DispatchPair> persisted = persist(events, happenedAt);
        for (DispatchPair pair : persisted) {
            if (pair.notification().getChannel() == NotificationChannel.IN_APP) {
                notificationStreamService.broadcast(pair.event(), pair.notification(), happenedAt);
            }
        }
    }

    @Transactional
    public NotificationResponse dispatchNow(NotificationOperationalEvent event, UUID currentUserId) {
        OffsetDateTime happenedAt = OffsetDateTime.now(ZoneOffset.UTC);
        List<DispatchPair> persisted = persist(List.of(event), happenedAt);
        DispatchPair pair = persisted.getFirst();
        if (pair.notification().getChannel() == NotificationChannel.IN_APP) {
            notificationStreamService.broadcast(pair.event(), pair.notification(), happenedAt);
        }
        return notificationMapper.toResponse(pair.notification(), currentUserId);
    }

    private List<DispatchPair> persist(Collection<NotificationOperationalEvent> events, OffsetDateTime happenedAt) {
        List<DispatchPair> pairs = new ArrayList<>();
        List<Notification> notifications = new ArrayList<>();

        for (NotificationOperationalEvent event : events) {
            NotificationTemplate template = notificationTemplateRepository
                    .findFirstByRestaurant_IdAndCodeAndChannelAndActiveTrue(
                            event.restaurantId(),
                            event.eventCode(),
                            event.channel()
                    )
                    .orElse(null);
            NotificationTemplateRenderer.RenderedNotification rendered = notificationTemplateRenderer.render(event, template, happenedAt);

            Notification notification = new Notification();
            notification.setRestaurant(restaurantRepository.getReferenceById(event.restaurantId()));
            if (event.branchId() != null) {
                notification.setBranch(branchRepository.getReferenceById(event.branchId()));
            }
            if (event.recipientUserId() != null) {
                notification.setRecipientUser(userRepository.getReferenceById(event.recipientUserId()));
            }
            notification.setTemplate(template);
            notification.setChannel(event.channel());
            notification.setPriority(event.priority());
            notification.setEventCode(event.eventCode());
            notification.setSubject(StringUtils.hasText(event.subject()) ? event.subject() : rendered.subject());
            notification.setBody(rendered.body());
            notification.setReferenceType(event.referenceType());
            notification.setReferenceId(event.referenceId());
            notification.setCreatedBy(event.actorId());
            notification.setUpdatedBy(event.actorId());

            if (event.channel() == NotificationChannel.IN_APP) {
                notification.setStatus(NotificationStatus.DELIVERED);
                notification.setSentAt(happenedAt);
                notification.setDeliveredAt(happenedAt);
            } else {
                notification.setStatus(NotificationStatus.QUEUED);
            }

            notifications.add(notification);
            pairs.add(new DispatchPair(event, notification));
        }

        List<Notification> saved = notificationRepository.saveAllAndFlush(notifications);
        List<DispatchPair> savedPairs = new ArrayList<>(saved.size());
        for (int i = 0; i < saved.size(); i++) {
            savedPairs.add(new DispatchPair(pairs.get(i).event(), saved.get(i)));
        }
        return savedPairs;
    }

    private record DispatchPair(NotificationOperationalEvent event, Notification notification) {
    }
}
