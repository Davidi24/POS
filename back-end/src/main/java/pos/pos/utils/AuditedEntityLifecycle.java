package pos.pos.utils;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface AuditedEntityLifecycle {

    UUID getId();

    void setId(UUID id);

    OffsetDateTime getCreatedAt();

    void setCreatedAt(OffsetDateTime createdAt);

    OffsetDateTime getUpdatedAt();

    void setUpdatedAt(OffsetDateTime updatedAt);
}
