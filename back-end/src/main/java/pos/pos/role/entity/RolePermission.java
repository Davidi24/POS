package pos.pos.role.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.github.f4b6a3.uuid.UuidCreator;

@Entity
@Table(
        name = "role_permissions",
        uniqueConstraints = {
                @UniqueConstraint(name = "role_permission_uq", columnNames = {"role_id", "permission_id"})
        },
        indexes = {
                @Index(name = "role_permissions_role_idx", columnList = "role_id"),
                @Index(name = "role_permissions_permission_idx", columnList = "permission_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermission {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "role_id", nullable = false, columnDefinition = "uuid")
    private UUID roleId;

    @Column(name = "permission_id", nullable = false, columnDefinition = "uuid")
    private UUID permissionId;

    @Column(name = "assigned_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime assignedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrdered();
        }

        if (assignedAt == null) {
            assignedAt = OffsetDateTime.now();
        }
    }
}