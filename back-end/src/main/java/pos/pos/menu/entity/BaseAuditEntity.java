package pos.pos.menu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.utils.AuditedEntityLifecycle;
import pos.pos.utils.EntityLifecycleUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Base entity providing shared audit fields
 * and primary identifier.
 *
 * Contains common fields inherited by domain entities,
 * including id, createdAt, and updatedAt.
 */

@Getter
@Setter
@MappedSuperclass
@NoArgsConstructor
public abstract class BaseAuditEntity implements AuditedEntityLifecycle {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void prePersist() {
        normalizeFields();
        validateState();
        EntityLifecycleUtils.initializeAuditedEntity(this);
    }

    @PreUpdate
    protected void preUpdate() {
        normalizeFields();
        validateState();
        EntityLifecycleUtils.touch(this);
    }

    protected void normalizeFields() {
    }

    protected void validateState() {
    }
}
