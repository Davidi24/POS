package pos.pos.settings.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.settings.entity.SettingsAuditLog;
import pos.pos.settings.repository.SettingsAuditLogRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettingsAuditService {

    private final SettingsAuditLogRepository settingsAuditLogRepository;

    public void log(
            Restaurant restaurant,
            Branch branch,
            String entityType,
            UUID entityId,
            String action,
            String message,
            UUID actorUserId
    ) {
        SettingsAuditLog auditLog = new SettingsAuditLog();
        auditLog.setRestaurant(restaurant);
        auditLog.setBranch(branch);
        auditLog.setEntityType(entityType);
        auditLog.setEntityId(entityId);
        auditLog.setAction(action);
        auditLog.setMessage(message);
        auditLog.setActorUserId(actorUserId);
        settingsAuditLogRepository.save(auditLog);
    }
}
