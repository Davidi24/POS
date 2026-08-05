package pos.pos.inventory.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.inventory.enums.InventoryCountStatus;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(
        name = "inventory_counts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inventory_counts_restaurant_count_number", columnNames = {"restaurant_id", "count_number"})
        },
        indexes = {
                @Index(name = "idx_inventory_counts_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_inventory_counts_branch_id", columnList = "branch_id"),
                @Index(name = "idx_inventory_counts_location_id", columnList = "location_id"),
                @Index(name = "idx_inventory_counts_status", columnList = "status"),
                @Index(name = "idx_inventory_counts_scheduled_at", columnList = "scheduled_at"),
                @Index(name = "idx_inventory_counts_approved_by", columnList = "approved_by"),
                @Index(name = "idx_inventory_counts_created_by", columnList = "created_by"),
                @Index(name = "idx_inventory_counts_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = """
        char_length(trim(count_number)) > 0
        AND status IN ('DRAFT', 'IN_PROGRESS', 'COMPLETED', 'APPROVED', 'CANCELLED')
        AND variance_value >= 0
        AND (
            approved_at IS NULL
            OR completed_at IS NOT NULL
        )
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class InventoryCount extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_inventory_counts_restaurant")
    )
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "branch_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_inventory_counts_branch")
    )
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "location_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_inventory_counts_location")
    )
    private InventoryLocation location;

    @Column(name = "count_number", nullable = false, length = 50)
    private String countNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private InventoryCountStatus status = InventoryCountStatus.DRAFT;

    @Column(name = "scheduled_at", columnDefinition = "timestamptz")
    private OffsetDateTime scheduledAt;

    @Column(name = "completed_at", columnDefinition = "timestamptz")
    private OffsetDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "approved_by",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_inventory_counts_approved_by_user")
    )
    private User approvedByUser;

    @Column(name = "approved_at", columnDefinition = "timestamptz")
    private OffsetDateTime approvedAt;

    @Column(name = "variance_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal varianceValue = BigDecimal.ZERO;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_counts_created_by_user")
    )
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "updated_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_counts_updated_by_user")
    )
    private User updatedByUser;

    /**
     * The list of individual item results counted in this session (the InventoryCountLine rows).
     */
    @OneToMany(mappedBy = "inventoryCount", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<InventoryCountLine> lines = new ArrayList<>();

    public void addLine(InventoryCountLine line) {
        if (line == null) {
            return;
        }

        lines.add(line);
        line.setInventoryCount(this);
    }

    public void removeLine(InventoryCountLine line) {
        if (line == null) {
            return;
        }

        lines.remove(line);
        line.setInventoryCount(null);
    }

    @Override
    protected void normalizeFields() {
        countNumber = NormalizationUtils.normalizeUpper(countNumber);
        notes = NormalizationUtils.normalize(notes);
        varianceValue = varianceValue == null ? BigDecimal.ZERO : varianceValue;
    }

    @Override
    protected void validateState() {
        if (varianceValue == null || varianceValue.signum() < 0) {
            throw new IllegalStateException("varianceValue must not be negative");
        }

        if (restaurant != null && branch != null && branch.getRestaurant() != null) {
            if (!Objects.equals(branch.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("inventory count branch must belong to the same restaurant");
            }
        }

        if (restaurant != null && location != null && location.getRestaurant() != null) {
            if (!Objects.equals(location.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("inventory count location must belong to the same restaurant");
            }
        }

        if (branch != null && location != null && location.getBranch() != null) {
            if (!Objects.equals(location.getBranch().getId(), branch.getId())) {
                throw new IllegalStateException("inventory count location must belong to the same branch");
            }
        }

        if (approvedAt != null && approvedByUser == null) {
            throw new IllegalStateException("approvedAt requires approvedByUser");
        }
    }
}
