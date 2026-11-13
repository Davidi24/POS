package pos.pos.role.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// One restaurant's change to a built-in role: a permission added to it there (granted) or taken away (not granted).
@Entity
@Table(name = "restaurant_role_permissions")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RestaurantRolePermission {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, columnDefinition = "uuid")
    private UUID restaurantId;

    @Column(name = "role_id", nullable = false, columnDefinition = "uuid")
    private UUID roleId;

    @Column(name = "permission_code", nullable = false, length = 100)
    private String permissionCode;

    @Column(name = "granted", nullable = false)
    private boolean granted;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", columnDefinition = "uuid")
    private UUID updatedBy;

    @PrePersist
    @PreUpdate
    protected void touch() {
        if (id == null) {
            id = UuidCreator.getTimeOrdered();
        }
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
