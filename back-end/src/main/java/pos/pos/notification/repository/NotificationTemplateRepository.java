package pos.pos.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.notification.entity.NotificationTemplate;
import pos.pos.notification.enums.NotificationChannel;

import java.util.List;
import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {

    List<NotificationTemplate> findAllByRestaurant_IdOrderByCodeAscChannelAsc(UUID restaurantId);

    java.util.Optional<NotificationTemplate> findByIdAndRestaurant_Id(UUID templateId, UUID restaurantId);

    java.util.Optional<NotificationTemplate> findFirstByRestaurant_IdAndCodeAndChannelAndActiveTrue(
            UUID restaurantId,
            String code,
            NotificationChannel channel
    );
}
