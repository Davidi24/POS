package pos.pos.menu.entity;

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

// What waiters see in Menu and at the till, per restaurant: "special menus only", or the menus marked shown_to_waiters.
@Entity
@Table(name = "menu_waiter_views")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class MenuWaiterView {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "restaurant_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID restaurantId;

    @Column(name = "special_menus_only", nullable = false)
    private boolean specialMenusOnly;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", columnDefinition = "uuid")
    private UUID updatedBy;

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
