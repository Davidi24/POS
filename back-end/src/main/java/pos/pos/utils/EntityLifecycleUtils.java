package pos.pos.utils;

import com.github.f4b6a3.uuid.UuidCreator;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class EntityLifecycleUtils {

    private EntityLifecycleUtils() {
    }

    public static void initializeAuditedEntity(AuditedEntityLifecycle entity) {
        if (entity.getId() == null) {
            entity.setId(UuidCreator.getTimeOrdered());
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }

        if (entity.getUpdatedAt() == null) {
            entity.setUpdatedAt(now);
        }
    }

    public static void touch(AuditedEntityLifecycle entity) {
        entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    }
}
