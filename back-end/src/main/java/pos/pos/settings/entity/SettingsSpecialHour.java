package pos.pos.settings.entity;

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
import pos.pos.restaurant.entity.Branch;
import pos.pos.utils.NormalizationUtils;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(
        name = "\"settings-special-hours\"",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_settings_special_hours_branch_date", columnNames = {"branch_id", "special_date"})
        },
        indexes = {
                @Index(name = "idx_settings_special_hours_branch_id", columnList = "branch_id")
        }
)
@Check(constraints = "note IS NULL OR char_length(note) <= 255")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class SettingsSpecialHour extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "branch_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_settings_special_hours_branch")
    )
    private Branch branch;

    @Column(name = "special_date", nullable = false, columnDefinition = "date")
    private LocalDate specialDate;

    @Column(name = "open_time", columnDefinition = "time")
    private LocalTime openTime;

    @Column(name = "close_time", columnDefinition = "time")
    private LocalTime closeTime;

    @Column(name = "is_closed", nullable = false)
    private boolean closed = false;

    @Column(name = "note", length = 255)
    private String note;

    @Override
    protected void normalizeFields() {
        note = NormalizationUtils.normalize(note);
    }

    @Override
    protected void validateState() {
        if (specialDate == null) {
            throw new IllegalStateException("specialDate is required");
        }

        if (closed) {
            openTime = null;
            closeTime = null;
            return;
        }

        if (openTime == null || closeTime == null) {
            throw new IllegalStateException("openTime and closeTime are required when the branch is open");
        }

        if (!closeTime.isAfter(openTime)) {
            throw new IllegalStateException("special hours must close after opening");
        }
    }
}
