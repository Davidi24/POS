package pos.pos.settings.entity;

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
import pos.pos.restaurant.entity.Branch;
import pos.pos.settings.enums.DepositType;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

@Entity
@Table(
        name = "\"settings-reservation-rules\"",
        indexes = {
                @Index(name = "idx_settings_reservation_rules_settings_id", columnList = "settings_id"),
                @Index(name = "idx_settings_reservation_rules_branch_id", columnList = "branch_id"),
                @Index(name = "idx_settings_reservation_rules_active_priority", columnList = "is_active, priority")
        }
)
@Check(constraints = """
        char_length(btrim(rule_name)) > 0
        AND priority >= 0
        AND advance_booking_days >= 0
        AND min_party_size > 0
        AND max_party_size >= min_party_size
        AND default_duration_minutes > 0
        AND buffer_minutes >= 0
        AND cancellation_window_hours >= 0
        AND (deposit_value IS NULL OR deposit_value >= 0)
        AND (effective_from IS NULL OR effective_to IS NULL OR effective_to > effective_from)
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class SettingsReservationRule extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "settings_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_settings_reservation_rules_settings")
    )
    private Settings settings;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "branch_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_settings_reservation_rules_branch")
    )
    private Branch branch;

    @Column(name = "rule_name", nullable = false, length = 120)
    private String ruleName;

    @Column(name = "priority", nullable = false)
    private int priority = 100;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "effective_from", columnDefinition = "timestamptz")
    private OffsetDateTime effectiveFrom;

    @Column(name = "effective_to", columnDefinition = "timestamptz")
    private OffsetDateTime effectiveTo;

    @Column(name = "advance_booking_days", nullable = false)
    private int advanceBookingDays = 30;

    @Column(name = "min_party_size", nullable = false)
    private int minPartySize = 1;

    @Column(name = "max_party_size", nullable = false)
    private int maxPartySize = 12;

    @Column(name = "default_duration_minutes", nullable = false)
    private int defaultDurationMinutes = 90;

    @Column(name = "buffer_minutes", nullable = false)
    private int bufferMinutes = 15;

    @Column(name = "allow_online_reservations", nullable = false)
    private boolean allowOnlineReservations = true;

    @Column(name = "require_deposit", nullable = false)
    private boolean requireDeposit = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "deposit_type", length = 20)
    private DepositType depositType;

    @Column(name = "deposit_value", precision = 12, scale = 2)
    private BigDecimal depositValue;

    @Column(name = "auto_confirm_reservations", nullable = false)
    private boolean autoConfirmReservations = false;

    @Column(name = "cancellation_window_hours", nullable = false)
    private int cancellationWindowHours = 24;

    @Override
    protected void normalizeFields() {
        ruleName = NormalizationUtils.normalize(ruleName);
    }

    @Override
    protected void validateState() {
        if (priority < 0) {
            throw new IllegalStateException("priority must not be negative");
        }

        if (advanceBookingDays < 0) {
            throw new IllegalStateException("advanceBookingDays must not be negative");
        }

        if (minPartySize <= 0) {
            throw new IllegalStateException("minPartySize must be greater than zero");
        }

        if (maxPartySize < minPartySize) {
            throw new IllegalStateException("maxPartySize must be greater than or equal to minPartySize");
        }

        if (defaultDurationMinutes <= 0) {
            throw new IllegalStateException("defaultDurationMinutes must be greater than zero");
        }

        if (bufferMinutes < 0) {
            throw new IllegalStateException("bufferMinutes must not be negative");
        }

        if (cancellationWindowHours < 0) {
            throw new IllegalStateException("cancellationWindowHours must not be negative");
        }

        if (effectiveFrom != null && effectiveTo != null && !effectiveTo.isAfter(effectiveFrom)) {
            throw new IllegalStateException("effectiveTo must be after effectiveFrom");
        }

        if (requireDeposit && (depositType == null || depositValue == null)) {
            throw new IllegalStateException("deposit configuration is incomplete");
        }

        if (depositValue != null && depositValue.signum() < 0) {
            throw new IllegalStateException("depositValue must not be negative");
        }

        if (settings != null && branch != null && branch.getRestaurant() != null && settings.getRestaurant() != null) {
            if (!Objects.equals(branch.getRestaurant().getId(), settings.getRestaurant().getId())) {
                throw new IllegalStateException("branch-specific reservation rule must use a branch from the same restaurant");
            }
        }
    }
}
