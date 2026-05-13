package pos.pos.shift.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.device.entity.Device;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.shift.enums.ShiftStatus;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(
        name = "shifts",
        indexes = {
                @Index(name = "idx_shifts_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_shifts_branch_id", columnList = "branch_id"),
                @Index(name = "idx_shifts_user_id", columnList = "user_id"),
                @Index(name = "idx_shifts_device_id", columnList = "device_id"),
                @Index(name = "idx_shifts_status", columnList = "status"),
                @Index(name = "idx_shifts_started_at", columnList = "started_at"),
                @Index(name = "idx_shifts_created_by", columnList = "created_by"),
                @Index(name = "idx_shifts_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = """
        status IN ('SCHEDULED', 'OPEN', 'ON_BREAK', 'CLOSED', 'MISSED', 'CANCELLED')
        AND regular_minutes >= 0
        AND overtime_minutes >= 0
        AND (hourly_rate IS NULL OR hourly_rate >= 0)
        AND (overtime_rate IS NULL OR overtime_rate >= 0)
        AND declared_cash_tips >= 0
        AND declared_card_tips >= 0
        AND sales_total >= 0
        AND cash_sales_total >= 0
        AND card_sales_total >= 0
        AND opening_drawer_amount >= 0
        AND expected_drawer_amount >= 0
        AND (actual_drawer_amount IS NULL OR actual_drawer_amount >= 0)
        AND (ended_at IS NULL OR started_at IS NOT NULL)
        AND (ended_at IS NULL OR ended_at >= started_at)
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Shift extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_shifts_restaurant")
    )
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "branch_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_shifts_branch")
    )
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_shifts_user")
    )
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "device_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_shifts_device")
    )
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ShiftStatus status = ShiftStatus.OPEN;

    @Column(name = "scheduled_start", columnDefinition = "timestamptz")
    private OffsetDateTime scheduledStart;

    @Column(name = "scheduled_end", columnDefinition = "timestamptz")
    private OffsetDateTime scheduledEnd;

    @Column(name = "started_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at", columnDefinition = "timestamptz")
    private OffsetDateTime endedAt;

    @Column(name = "hourly_rate", precision = 12, scale = 2)
    private BigDecimal hourlyRate;

    @Column(name = "overtime_rate", precision = 12, scale = 2)
    private BigDecimal overtimeRate;

    @Column(name = "regular_minutes", nullable = false)
    private int regularMinutes = 0;

    @Column(name = "overtime_minutes", nullable = false)
    private int overtimeMinutes = 0;

    @Column(name = "declared_cash_tips", nullable = false, precision = 19, scale = 2)
    private BigDecimal declaredCashTips = BigDecimal.ZERO;

    @Column(name = "declared_card_tips", nullable = false, precision = 19, scale = 2)
    private BigDecimal declaredCardTips = BigDecimal.ZERO;

    @Column(name = "sales_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal salesTotal = BigDecimal.ZERO;

    @Column(name = "cash_sales_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal cashSalesTotal = BigDecimal.ZERO;

    @Column(name = "card_sales_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal cardSalesTotal = BigDecimal.ZERO;

    @Column(name = "opening_drawer_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingDrawerAmount = BigDecimal.ZERO;

    @Column(name = "expected_drawer_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedDrawerAmount = BigDecimal.ZERO;

    @Column(name = "actual_drawer_amount", precision = 19, scale = 2)
    private BigDecimal actualDrawerAmount;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_shifts_created_by_user")
    )
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "updated_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_shifts_updated_by_user")
    )
    private User updatedByUser;

    @OneToMany(mappedBy = "shift", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startedAt ASC")
    private List<ShiftBreak> breaks = new ArrayList<>();

    public void addBreak(ShiftBreak shiftBreak) {
        if (shiftBreak == null) {
            return;
        }

        breaks.add(shiftBreak);
        shiftBreak.setShift(this);
    }

    public void removeBreak(ShiftBreak shiftBreak) {
        if (shiftBreak == null) {
            return;
        }

        breaks.remove(shiftBreak);
        shiftBreak.setShift(null);
    }

    @Override
    protected void normalizeFields() {
        notes = NormalizationUtils.normalize(notes);
        hourlyRate = nullableMoney(hourlyRate);
        overtimeRate = nullableMoney(overtimeRate);
        declaredCashTips = defaultMoney(declaredCashTips);
        declaredCardTips = defaultMoney(declaredCardTips);
        salesTotal = defaultMoney(salesTotal);
        cashSalesTotal = defaultMoney(cashSalesTotal);
        cardSalesTotal = defaultMoney(cardSalesTotal);
        openingDrawerAmount = defaultMoney(openingDrawerAmount);
        expectedDrawerAmount = defaultMoney(expectedDrawerAmount);
        actualDrawerAmount = nullableMoney(actualDrawerAmount);

        if (startedAt == null) {
            startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }

    @Override
    protected void validateState() {
        if (regularMinutes < 0 || overtimeMinutes < 0) {
            throw new IllegalStateException("shift minutes must not be negative");
        }

        validateNonNegative(hourlyRate, "hourlyRate");
        validateNonNegative(overtimeRate, "overtimeRate");
        validateNonNegative(declaredCashTips, "declaredCashTips");
        validateNonNegative(declaredCardTips, "declaredCardTips");
        validateNonNegative(salesTotal, "salesTotal");
        validateNonNegative(cashSalesTotal, "cashSalesTotal");
        validateNonNegative(cardSalesTotal, "cardSalesTotal");
        validateNonNegative(openingDrawerAmount, "openingDrawerAmount");
        validateNonNegative(expectedDrawerAmount, "expectedDrawerAmount");
        validateNonNegative(actualDrawerAmount, "actualDrawerAmount");

        if (restaurant != null && branch != null && branch.getRestaurant() != null) {
            if (!Objects.equals(branch.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("shift branch must belong to the same restaurant");
            }
        }

        if (user != null && restaurant != null && user.getRestaurantId() != null) {
            if (!Objects.equals(user.getRestaurantId(), restaurant.getId())) {
                throw new IllegalStateException("shift user must belong to the same restaurant");
            }
        }

        if (device != null && restaurant != null && device.getRestaurant() != null) {
            if (!Objects.equals(device.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("shift device must belong to the same restaurant");
            }
        }

        if (device != null && branch != null && device.getBranch() != null) {
            if (!Objects.equals(device.getBranch().getId(), branch.getId())) {
                throw new IllegalStateException("shift device must belong to the same branch");
            }
        }

        if (scheduledStart != null && scheduledEnd != null && scheduledEnd.isBefore(scheduledStart)) {
            throw new IllegalStateException("scheduledEnd must not be before scheduledStart");
        }

        if (endedAt != null && endedAt.isBefore(startedAt)) {
            throw new IllegalStateException("endedAt must not be before startedAt");
        }
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal nullableMoney(BigDecimal value) {
        return value == null ? null : value;
    }

    private void validateNonNegative(BigDecimal value, String fieldName) {
        if (value != null && value.signum() < 0) {
            throw new IllegalStateException(fieldName + " must not be negative");
        }
    }
}
