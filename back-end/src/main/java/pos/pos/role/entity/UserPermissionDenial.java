package pos.pos.role.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// One permission switched off for one person, whatever their roles grant (the Super Admin's ticks per Co-Super-Admin).
@Entity
@Table(name = "user_permission_denials")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserPermissionDenial {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "permission_code", nullable = false, length = 100)
    private String permissionCode;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy;

    @PrePersist
    protected void prePersist() {
        if (id == null) {
            id = UuidCreator.getTimeOrdered();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
