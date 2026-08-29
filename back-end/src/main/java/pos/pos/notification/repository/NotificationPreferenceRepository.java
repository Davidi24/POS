package pos.pos.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.notification.entity.NotificationPreference;
import pos.pos.notification.enums.NotificationChannel;

import java.util.List;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    List<NotificationPreference> findAllByUser_IdOrderByChannelAscEventCodeAsc(UUID userId);

    List<NotificationPreference> findAllByUser_IdAndChannelAndEnabledFalse(UUID userId, NotificationChannel channel);

    java.util.Optional<NotificationPreference> findByUser_IdAndChannelAndEventCode(
            UUID userId,
            NotificationChannel channel,
            String eventCode
    );
}
