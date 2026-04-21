package pos.pos.role.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Check;
import pos.pos.utils.AuditedEntityLifecycle;
import pos.pos.utils.EntityLifecycleUtils;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "roles",
        indexes = {
                @Index(name = "idx_roles_rank", columnList = "rank"),
                @Index(name = "idx_roles_active_rank", columnList = "is_active, rank"),
                @Index(name = "idx_roles_deleted_at", columnList = "deleted_at")
        }
)
@Check(constraints = "rank > 0")
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Role implements AuditedEntityLifecycle {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Builder.Default
    @Column(name = "rank", nullable = false)
    private long rank = 10_000L;

    @Builder.Default
    @Column(name = "is_system", nullable = false)
    private boolean isSystem = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Builder.Default
    @Column(name = "is_assignable", nullable = false)
    private boolean assignable = true;

    @Builder.Default
    @Column(name = "is_protected", nullable = false)
    private boolean protectedRole = false;

    @Column(name = "deleted_at", columnDefinition = "timestamptz")
    private OffsetDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        normalizeFields();
        EntityLifecycleUtils.initializeAuditedEntity(this);
    }

    @PreUpdate
    public void preUpdate() {
        normalizeFields();
        EntityLifecycleUtils.touch(this);
    }

    private void normalizeFields() {
        if (code == null && name != null) {
            code = name.replaceAll("[^A-Za-z0-9]+", "_");
        }

        code = NormalizationUtils.normalizeUpper(code);
        name = NormalizationUtils.normalize(name);
        description = NormalizationUtils.normalize(description);
    }
}
