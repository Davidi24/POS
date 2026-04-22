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

import java.time.LocalTime;

@Entity
@Table(
        name = "\"settings-business-hours\"",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_settings_business_hours_branch_day", columnNames = {"branch_id", "day_of_week"})
        },
        indexes = {
                @Index(name = "idx_settings_business_hours_branch_id", columnList = "branch_id")
        }
)
@Check(constraints = "day_of_week BETWEEN 1 AND 7")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class SettingsBusinessHour extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "branch_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_settings_business_hours_branch")
    )
    private Branch branch;

    @Column(name = "day_of_week", nullable = false)
    private int dayOfWeek;

    @Column(name = "open_time", columnDefinition = "time")
    private LocalTime openTime;

    @Column(name = "close_time", columnDefinition = "time")
    private LocalTime closeTime;

    @Column(name = "is_closed", nullable = false)
    private boolean closed = false;

    @Column(name = "is_overnight", nullable = false)
    private boolean overnight = false;

    @Override
    protected void validateState() {
        if (dayOfWeek < 1 || dayOfWeek > 7) {
            throw new IllegalStateException("dayOfWeek must be between 1 and 7");
        }

        if (closed) {
            openTime = null;
            closeTime = null;
            overnight = false;
            return;
        }

        if (openTime == null || closeTime == null) {
            throw new IllegalStateException("openTime and closeTime are required when the branch is open");
        }

        if (openTime.equals(closeTime)) {
            throw new IllegalStateException("openTime and closeTime must not be equal");
        }

        if (overnight && !closeTime.isBefore(openTime)) {
            throw new IllegalStateException("overnight hours must end on the following day");
        }

        if (!overnight && !closeTime.isAfter(openTime)) {
            throw new IllegalStateException("non-overnight hours must close after opening");
        }
    }
}
