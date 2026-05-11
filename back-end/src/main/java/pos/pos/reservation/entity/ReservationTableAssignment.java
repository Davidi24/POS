package pos.pos.reservation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.tables.entity.RestaurantTable;
import pos.pos.user.entity.User;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "reservation_tables",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_reservation_tables_reservation_table", columnNames = {"reservation_id", "table_id"})
        },
        indexes = {
                @Index(name = "idx_reservation_tables_reservation_id", columnList = "reservation_id"),
                @Index(name = "idx_reservation_tables_table_id", columnList = "table_id"),
                @Index(name = "idx_reservation_tables_assigned_by", columnList = "assigned_by")
        }
)
@Check(constraints = "assigned_at IS NOT NULL")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ReservationTableAssignment extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "reservation_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_reservation_tables_reservation")
    )
    private Reservation reservation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "table_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_reservation_tables_table")
    )
    private RestaurantTable restaurantTable;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryAssignment = false;

    @Column(name = "assigned_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime assignedAt;

    @Column(name = "assigned_by", columnDefinition = "uuid")
    private UUID assignedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "assigned_by",
            insertable = false,
            updatable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_reservation_tables_assigned_by_user")
    )
    private User assignedByUser;

    @Override
    protected void normalizeFields() {
        if (assignedAt == null) {
            assignedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @Override
    protected void validateState() {
        if (reservation != null && restaurantTable != null
                && reservation.getRestaurant() != null && restaurantTable.getRestaurant() != null) {
            if (!Objects.equals(reservation.getRestaurant().getId(), restaurantTable.getRestaurant().getId())) {
                throw new IllegalStateException("reservation table assignment must stay within one restaurant");
            }
        }

        if (reservation != null && restaurantTable != null
                && reservation.getBranch() != null && restaurantTable.getBranch() != null) {
            if (!Objects.equals(reservation.getBranch().getId(), restaurantTable.getBranch().getId())) {
                throw new IllegalStateException("reservation table assignment must stay within one branch");
            }
        }
    }
}
