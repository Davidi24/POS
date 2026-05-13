package pos.pos.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pos.pos.audit.entity.AuditLog;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
}
