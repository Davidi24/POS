package pos.pos.role.entity;

import jakarta.persistence.*;
import lombok.*;
import pos.pos.utils.AuditedEntityLifecycle;
import pos.pos.utils.EntityLifecycleUtils;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "roles",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_roles_code", columnNames = "code"),
                @UniqueConstraint(name = "uk_roles_name", columnNames = "name")
        }
)
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
    @Column(name = "is_system", nullable = false)
    private boolean isSystem = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

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
