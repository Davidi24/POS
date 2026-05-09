package pos.pos.reservation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

// this is class is used when users of stuff makes note to send to each other in a specific reservation
@Entity
@Table(
        name = "reservation_notes",
        indexes = {
                @Index(name = "idx_reservation_notes_reservation_id", columnList = "reservation_id"),
                @Index(name = "idx_reservation_notes_created_by", columnList = "created_by"),
                @Index(name = "idx_reservation_notes_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = "char_length(btrim(note)) > 0")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ReservationNote extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "reservation_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_reservation_notes_reservation")
    )
    private Reservation reservation;

    @Column(name = "note", nullable = false, columnDefinition = "text")
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_reservation_notes_created_by_user")
    )
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "updated_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_reservation_notes_updated_by_user")
    )
    private User updatedByUser;

    @Override
    protected void normalizeFields() {
        note = NormalizationUtils.normalize(note);
    }

    @Override
    protected void validateState() {
        if (note == null) {
            throw new IllegalStateException("note is required");
        }
    }
}
