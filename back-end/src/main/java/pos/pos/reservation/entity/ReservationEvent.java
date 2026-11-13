package pos.pos.reservation.entity;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.reservation.enums.ReservationEventType;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

// One change to a booking that isn't a status change, e.g. "Table held until 19:45" with who did it and why.
@Entity
@Table(name = "reservation_events")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ReservationEvent {

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "reservation_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_reservation_events_reservation")
    )
    private Reservation reservation;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private ReservationEventType type;

    @Column(name = "detail", columnDefinition = "text")
    private String detail;

    @Column(name = "reason", columnDefinition = "text")
    private String reason;

    @Column(name = "actor_id", columnDefinition = "uuid")
    private UUID actorId;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void prePersist() {
        detail = NormalizationUtils.normalize(detail);
        reason = NormalizationUtils.normalize(reason);
        if (type == null) {
            throw new IllegalStateException("type is required");
        }
        if (id == null) {
            id = UuidCreator.getTimeOrdered();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
