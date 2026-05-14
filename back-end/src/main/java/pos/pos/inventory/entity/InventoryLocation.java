package pos.pos.inventory.entity;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.inventory.enums.InventoryLocationType;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(
        name = "inventory_locations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inventory_locations_restaurant_code", columnNames = {"restaurant_id", "code"})
        },
        indexes = {
                @Index(name = "idx_inventory_locations_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_inventory_locations_branch_id", columnList = "branch_id"),
                @Index(name = "idx_inventory_locations_location_type", columnList = "location_type"),
                @Index(name = "idx_inventory_locations_created_by", columnList = "created_by"),
                @Index(name = "idx_inventory_locations_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = """
        char_length(btrim(code)) > 0
        AND char_length(btrim(name)) > 0
        AND location_type IN (
            'BRANCH_STORAGE',
            'KITCHEN',
            'BAR',
            'WALK_IN',
            'FREEZER',
            'DRY_STORAGE',
            'CENTRAL_WAREHOUSE',
            'TRANSIT'
        )
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class InventoryLocation extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_inventory_locations_restaurant")
    )
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "branch_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_inventory_locations_branch")
    )
    private Branch branch;

    @Column(name = "code", nullable = false, length = 80)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false, length = 30)
    private InventoryLocationType locationType = InventoryLocationType.BRANCH_STORAGE;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_locations_created_by_user")
    )
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "updated_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_locations_updated_by_user")
    )
    private User updatedByUser;

    @OneToMany(mappedBy = "location")
    private List<InventoryLevel> levels = new ArrayList<>();

    @OneToMany(mappedBy = "location")
    private List<InventoryMovement> movements = new ArrayList<>();

    @OneToMany(mappedBy = "location")
    private List<InventoryCount> counts = new ArrayList<>();

    @Override
    protected void normalizeFields() {
        code = NormalizationUtils.normalizeCode(code == null ? name : code, 80);
        name = NormalizationUtils.normalize(name);
        notes = NormalizationUtils.normalize(notes);
    }

    @Override
    protected void validateState() {
        if (restaurant != null && branch != null && branch.getRestaurant() != null) {
            if (!Objects.equals(branch.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("inventory location branch must belong to the same restaurant");
            }
        }
    }
}
