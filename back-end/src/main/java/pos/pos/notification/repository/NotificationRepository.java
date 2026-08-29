package pos.pos.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pos.pos.notification.entity.Notification;
import pos.pos.notification.enums.NotificationChannel;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("""
            select n from Notification n
            where n.restaurant.id = :restaurantId
              and (n.recipientUser is null or n.recipientUser.id = :userId)
              and (:branchId is null or n.branch is null or n.branch.id = :branchId)
              and (:channel is null or n.channel = :channel)
              and (:topicPrefix is null or upper(n.eventCode) like concat(:topicPrefix, '%'))
              and (:eventCode is null or upper(n.eventCode) = :eventCode)
              and (:personalOnly = false or n.recipientUser.id = :userId)
              and (:unreadOnly = false or (n.recipientUser.id = :userId and n.readAt is null))
            """)
    Page<Notification> searchFeed(
            UUID restaurantId,
            UUID userId,
            UUID branchId,
            NotificationChannel channel,
            String topicPrefix,
            String eventCode,
            boolean personalOnly,
            boolean unreadOnly,
            Pageable pageable
    );

    java.util.Optional<Notification> findByIdAndRestaurant_Id(UUID id, UUID restaurantId);
}
