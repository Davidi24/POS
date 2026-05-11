package pos.pos.tables.entity;

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
import pos.pos.reservation.entity.ReservationTableAssignment;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.tables.enums.TableShape;
import pos.pos.tables.enums.TableStatus;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// RestaurantTable represents one real physical table in the restaurant, for example A1, A2, P1, or VIP1.
// This is the actual table that has its own capacity, position, status, QR code, and can be assigned to reservations.
@Entity
@Table(
        name = "tables",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tables_branch_table_number", columnNames = {"branch_id", "table_number"})
        },
        indexes = {
                @Index(name = "idx_tables_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_tables_branch_id", columnList = "branch_id"),
                @Index(name = "idx_tables_category_id", columnList = "category_id"),
                @Index(name = "idx_tables_status", columnList = "status"),
                @Index(name = "idx_tables_created_by", columnList = "created_by"),
                @Index(name = "idx_tables_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = """
        char_length(btrim(table_number)) > 0
        AND capacity > 0
        AND (name IS NULL OR char_length(btrim(name)) > 0)
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class RestaurantTable extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_tables_restaurant")
    )
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "branch_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_tables_branch")
    )
    private Branch branch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "category_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_tables_category")
    )
    private TableCategory category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "merged_into_table_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_tables_merged_into")
    )
    private RestaurantTable mergedInto;

    // identifier
    @Column(name = "table_number", nullable = false, length = 30)
    private String tableNumber;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "capacity", nullable = false)
    private int capacity = 2;

    @Column(name = "floor", length = 50)
    private String floor;

    @Column(name = "position_x", precision = 10, scale = 2)
    private BigDecimal positionX;

    @Column(name = "position_y", precision = 10, scale = 2)
    private BigDecimal positionY;

    @Enumerated(EnumType.STRING)
    @Column(name = "shape", nullable = false, length = 30)
    private TableShape shape = TableShape.RECTANGLE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private TableStatus status = TableStatus.AVAILABLE;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "qr_code_value", length = 255)
    private String qrCodeValue;

    @OneToMany(mappedBy = "restaurantTable")
    @OrderBy("assignedAt ASC, createdAt ASC")
    private List<ReservationTableAssignment> reservationAssignments = new ArrayList<>();

    @OneToMany(mappedBy = "mergedInto")
    @OrderBy("tableNumber ASC")
    private List<RestaurantTable> mergedChildren = new ArrayList<>();

    @Override
    protected void normalizeFields() {
        tableNumber = NormalizationUtils.normalizeCode(tableNumber);
        name = NormalizationUtils.normalize(name == null ? tableNumber : name);
        floor = NormalizationUtils.normalize(floor);
        qrCodeValue = NormalizationUtils.normalize(qrCodeValue);
    }

    @Override
    protected void validateState() {
        if (capacity <= 0) {
            throw new IllegalStateException("capacity must be greater than zero");
        }

        if ((positionX == null) != (positionY == null)) {
            throw new IllegalStateException("positionX and positionY must both be set together");
        }

        if (restaurant != null && branch != null && branch.getRestaurant() != null) {
            if (!Objects.equals(branch.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("table branch must belong to the same restaurant");
            }
        }

        if (category != null && branch != null && category.getBranch() != null) {
            if (!Objects.equals(category.getBranch().getId(), branch.getId())) {
                throw new IllegalStateException("table category must belong to the same branch");
            }
        }

        if (mergedInto != null) {
            if (Objects.equals(mergedInto.getId(), getId())) {
                throw new IllegalStateException("table cannot be merged into itself");
            }

            if (branch != null && mergedInto.getBranch() != null) {
                if (!Objects.equals(mergedInto.getBranch().getId(), branch.getId())) {
                    throw new IllegalStateException("merged tables must belong to the same branch");
                }
            }

            if (restaurant != null && mergedInto.getRestaurant() != null) {
                if (!Objects.equals(mergedInto.getRestaurant().getId(), restaurant.getId())) {
                    throw new IllegalStateException("merged tables must belong to the same restaurant");
                }
            }
        }
    }
}
