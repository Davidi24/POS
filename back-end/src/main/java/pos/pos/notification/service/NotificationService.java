package pos.pos.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.common.dto.PageResponse;
import pos.pos.exception.auth.AuthException;
import pos.pos.notification.dto.CreateNotificationBroadcastRequest;
import pos.pos.notification.dto.NotificationResponse;
import pos.pos.notification.entity.Notification;
import pos.pos.notification.enums.NotificationMutationType;
import pos.pos.notification.enums.NotificationTopic;
import pos.pos.notification.mapper.NotificationMapper;
import pos.pos.notification.repository.NotificationRepository;
import pos.pos.notification.support.NotificationEventCodeSupport;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;
import pos.pos.utils.NormalizationUtils;
import pos.pos.utils.PageableUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int DEFAULT_PAGE_SIZE = 25;

    private final NotificationRepository notificationRepository;
    private final NotificationMapper notificationMapper;
    private final NotificationOperationalPublisher notificationOperationalPublisher;
    private final NotificationDispatchService notificationDispatchService;
    private final RestaurantScopeService restaurantScopeService;
    private final ActorScopeService actorScopeService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getFeed(
            Authentication authentication,
            UUID restaurantId,
            UUID branchId,
            pos.pos.notification.enums.NotificationChannel channel,
            NotificationTopic topic,
            String eventCode,
            boolean unreadOnly,
            boolean personalOnly,
            Integer page,
            Integer size,
            String direction
    ) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        if (branchId != null) {
            restaurantScopeService.requireAccessibleBranch(authentication, restaurantId, branchId);
        }

        UUID currentUserId = actorScopeService.currentUserId(authentication);
        Pageable pageable = PageableUtils.create(page, size, direction, "createdAt", DEFAULT_PAGE_SIZE);
        Page<NotificationResponse> feed = notificationRepository.searchFeed(
                        restaurantId,
                        currentUserId,
                        branchId,
                        channel,
                        topic == null ? null : topic.name() + "_",
                        NormalizationUtils.normalizeUpper(eventCode),
                        personalOnly,
                        unreadOnly,
                        pageable
                )
                .map(notification -> notificationMapper.toResponse(notification, currentUserId));

        return PageResponse.from(feed);
    }

    @Transactional
    public NotificationResponse markRead(Authentication authentication, UUID restaurantId, UUID notificationId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        UUID currentUserId = actorScopeService.currentUserId(authentication);

        Notification notification = notificationRepository.findByIdAndRestaurant_Id(notificationId, restaurantId)
                .orElseThrow(() -> new AuthException("Notification not found", HttpStatus.NOT_FOUND));
        if (notification.getRecipientUser() == null || !currentUserId.equals(notification.getRecipientUser().getId())) {
            throw new AuthException("Only personal notifications can be marked as read", HttpStatus.FORBIDDEN);
        }

        if (notification.getReadAt() == null) {
            notification.setReadAt(OffsetDateTime.now(ZoneOffset.UTC));
            notification.setStatus(pos.pos.notification.enums.NotificationStatus.READ);
            notification.setUpdatedBy(currentUserId);
            notificationRepository.saveAndFlush(notification);
        }

        return notificationMapper.toResponse(notification, currentUserId);
    }

    @Transactional
    public NotificationResponse broadcast(
            Authentication authentication,
            UUID restaurantId,
            CreateNotificationBroadcastRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = actorScopeService.currentUserId(authentication);

        UUID branchId = request.getBranchId();
        if (branchId != null) {
            restaurantScopeService.requireManageableBranch(authentication, restaurantId, branchId);
        }

        UUID recipientUserId = request.getRecipientUserId();
        if (recipientUserId != null) {
            User recipient = userRepository.findActiveById(recipientUserId)
                    .orElseThrow(() -> new AuthException("Recipient user not found", HttpStatus.NOT_FOUND));
            if (!restaurantId.equals(recipient.getRestaurantId())) {
                throw new AuthException("Recipient user must belong to the same restaurant", HttpStatus.BAD_REQUEST);
            }
        }

        String referenceType = request.getReferenceType();
        if (referenceType == null && request.getTopic() != null) {
            referenceType = request.getTopic().name();
        }

        NotificationOperationalEvent event = new NotificationOperationalEvent(
                request.getTopic(),
                NotificationMutationType.BROADCAST,
                request.getChannel() == null ? pos.pos.notification.enums.NotificationChannel.IN_APP : request.getChannel(),
                request.getPriority() == null ? pos.pos.notification.enums.NotificationPriority.NORMAL : request.getPriority(),
                NotificationEventCodeSupport.normalizeEventCode(
                        request.getEventCode(),
                        request.getTopic(),
                        NotificationMutationType.BROADCAST,
                        referenceType
                ),
                restaurantId,
                branchId,
                recipientUserId,
                NotificationEventCodeSupport.toToken(referenceType),
                request.getReferenceId(),
                actorId,
                request.getSubject(),
                request.getBody()
        );

        return notificationDispatchService.dispatchNow(event, actorId);
    }

    public void publishUserRoleChange(
            UUID restaurantId,
            UUID userId,
            UUID actorId
    ) {
        notificationOperationalPublisher.publishCustom(
                NotificationTopic.USER,
                NotificationMutationType.STATE_CHANGE,
                pos.pos.notification.enums.NotificationChannel.IN_APP,
                pos.pos.notification.enums.NotificationPriority.NORMAL,
                "USER_USER_ROLE_STATE_CHANGE",
                restaurantId,
                null,
                null,
                "USER",
                userId,
                actorId,
                null,
                "User role assignments changed"
        );
    }

    public void publishRoleChange(
            UUID restaurantId,
            UUID roleId,
            UUID actorId,
            String message
    ) {
        notificationOperationalPublisher.publishCustom(
                NotificationTopic.ROLE,
                NotificationMutationType.STATE_CHANGE,
                pos.pos.notification.enums.NotificationChannel.IN_APP,
                pos.pos.notification.enums.NotificationPriority.NORMAL,
                "ROLE_ROLE_PERMISSION_STATE_CHANGE",
                restaurantId,
                null,
                null,
                "ROLE",
                roleId,
                actorId,
                null,
                message
        );
    }
}
