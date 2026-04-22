package pos.pos.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.utils.AuditedEntityLifecycle;
import pos.pos.utils.EntityLifecycleUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class AbstractTimestampedEntity implements AuditedEntityLifecycle {

    @Id
    @EqualsAndHashCode.Include
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
