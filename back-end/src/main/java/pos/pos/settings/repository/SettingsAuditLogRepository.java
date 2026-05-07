package pos.pos.settings.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.settings.entity.SettingsAuditLog;

import java.util.UUID;

public interface SettingsAuditLogRepository extends JpaRepository<SettingsAuditLog, UUID> {

    Page<SettingsAuditLog> findAllByRestaurant_IdOrderByCreatedAtDesc(UUID restaurantId, Pageable pageable);
}
