package pos.pos.reservation.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.reservation.enums.ReservationStatus;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(
        name = "\"reservation-status-history\"",
        indexes = {
                @Index(name = "idx_reservation_status_history_reservation_id", columnList = "reservation_id"),
                @Index(name = "idx_reservation_status_history_changed_by", columnList = "changed_by"),
                @Index(name = "idx_reservation_status_history_changed_at", columnList = "changed_at")
        }
)
@Check(constraints = """
        new_status IS NOT NULL
        AND (old_status IS NULL OR old_status <> new_status)
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ReservationStatusHistory {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "reservation_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_reservation_status_history_reservation")
    )
    private Reservation reservation;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 30)
    private ReservationStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private ReservationStatus newStatus;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "changed_by", columnDefinition = "uuid")
    private UUID changedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "changed_by",
            insertable = false,
            updatable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_reservation_status_history_changed_by_user")
    )
    private User changedByUser;

    @Column(name = "changed_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime changedAt;

    @PrePersist
    protected void prePersist() {
        normalizeFields();
        validateState();

        if (id == null) {
            id = UuidCreator.getTimeOrdered();
        }

        if (changedAt == null) {
            changedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @PreUpdate
    protected void preUpdate() {
        normalizeFields();
        validateState();
    }

    private void normalizeFields() {
        reason = NormalizationUtils.normalize(reason);
    }

    private void validateState() {
        if (newStatus == null) {
            throw new IllegalStateException("newStatus is required");
        }

        if (oldStatus != null && oldStatus == newStatus) {
            throw new IllegalStateException("oldStatus and newStatus must differ");
        }
    }
}
