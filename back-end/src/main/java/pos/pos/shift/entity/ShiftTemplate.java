package pos.pos.shift.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

// A usual shift of a restaurant ("Morning 09:00–17:00"), for the roles in roleIds (none = everyone).
@Entity
@Table(name = "shift_templates")
@Getter
@Setter
@NoArgsConstructor
public class ShiftTemplate {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, columnDefinition = "uuid")
    private UUID restaurantId;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    // One of the app's shift icons, by name; none = the part of the day it starts in.
    @Column(name = "icon", length = 40)
    private String icon;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "shift_template_roles", joinColumns = @JoinColumn(name = "template_id"))
    @Column(name = "role_id", columnDefinition = "uuid")
    private Set<UUID> roleIds = new LinkedHashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void created() {
        if (id == null) id = UuidCreator.getTimeOrdered();
        createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void touched() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
