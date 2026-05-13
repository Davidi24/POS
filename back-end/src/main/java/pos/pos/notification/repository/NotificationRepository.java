package pos.pos.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.notification.entity.Notification;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
}
