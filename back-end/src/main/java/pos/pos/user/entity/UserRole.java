package pos.pos.user.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.github.f4b6a3.uuid.UuidCreator;

@Entity
@Table(
        name = "user_roles",
        uniqueConstraints = {
                @UniqueConstraint(name = "user_role_uq", columnNames = {"user_id", "role_id"})
        },
        indexes = {
                @Index(name = "user_roles_user_idx", columnList = "user_id"),
                @Index(name = "user_roles_role_idx", columnList = "role_id")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "role_id", nullable = false, columnDefinition = "uuid")
    private UUID roleId;

    @Column(name = "assigned_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime assignedAt;

    @PrePersist
    public void generateId() {
        if (id == null) {
            id = UuidCreator.getTimeOrdered();
        }
    }
}