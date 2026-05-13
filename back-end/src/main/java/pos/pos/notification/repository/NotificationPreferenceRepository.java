package pos.pos.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.notification.entity.NotificationPreference;

import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
}
