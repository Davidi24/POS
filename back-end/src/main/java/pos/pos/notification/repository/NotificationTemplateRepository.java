package pos.pos.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.notification.entity.NotificationTemplate;

import java.util.UUID;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, UUID> {
}
