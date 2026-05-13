package pos.pos.shift.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.shift.enums.ShiftBreakType;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "shift_breaks",
        indexes = {
                @Index(name = "idx_shift_breaks_shift_id", columnList = "shift_id"),
                @Index(name = "idx_shift_breaks_break_type", columnList = "break_type"),
                @Index(name = "idx_shift_breaks_started_at", columnList = "started_at")
        }
)
@Check(constraints = """
        break_type IN ('REST', 'MEAL', 'PAID_BREAK', 'UNPAID_BREAK')
        AND started_at IS NOT NULL
        AND (ended_at IS NULL OR ended_at >= started_at)
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class ShiftBreak extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "shift_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_shift_breaks_shift")
    )
    private Shift shift;

    @Enumerated(EnumType.STRING)
    @Column(name = "break_type", nullable = false, length = 30)
    private ShiftBreakType breakType = ShiftBreakType.REST;

    @Column(name = "is_paid", nullable = false)
    private boolean paid = false;

    @Column(name = "started_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at", columnDefinition = "timestamptz")
    private OffsetDateTime endedAt;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Override
    protected void normalizeFields() {
        notes = NormalizationUtils.normalize(notes);
    }

    @Override
    protected void validateState() {
        if (startedAt == null) {
            throw new IllegalStateException("startedAt is required");
        }

        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalStateException("endedAt must not be before startedAt");
        }
    }
}
