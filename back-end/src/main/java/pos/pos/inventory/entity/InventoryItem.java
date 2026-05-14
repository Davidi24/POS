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
import pos.pos.common.entity.AbstractAuditedSoftDeleteEntity;
import pos.pos.inventory.enums.InventoryItemType;
import pos.pos.inventory.enums.InventoryUnit;
import pos.pos.recipe.entity.RecipeComponent;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "inventory_items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inventory_items_restaurant_code", columnNames = {"restaurant_id", "code"})
        },
        indexes = {
                @Index(name = "idx_inventory_items_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_inventory_items_item_type", columnList = "item_type"),
                @Index(name = "idx_inventory_items_base_unit", columnList = "base_unit"),
                @Index(name = "idx_inventory_items_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_inventory_items_created_by", columnList = "created_by"),
                @Index(name = "idx_inventory_items_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = """
        char_length(btrim(code)) > 0
        AND char_length(btrim(name)) > 0
        AND item_type IN (
            'INGREDIENT',
            'PREPARED_COMPONENT',
            'FINISHED_GOOD',
            'BEVERAGE',
            'ALCOHOL',
            'PACKAGING',
            'SUPPLY'
        )
        AND base_unit IN (
            'EACH',
            'GRAM',
            'KILOGRAM',
            'MILLILITER',
            'LITER',
            'OUNCE',
            'POUND',
            'CUP',
            'TABLESPOON',
            'TEASPOON',
            'PORTION',
            'CASE',
            'BOTTLE',
            'PACK',
            'TRAY'
        )
        AND cost_per_unit >= 0
        AND (reorder_point IS NULL OR reorder_point >= 0)
        AND (par_level IS NULL OR par_level >= 0)
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class InventoryItem extends AbstractAuditedSoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_inventory_items_restaurant")
    )
    private Restaurant restaurant;

    @Column(name = "code", nullable = false, length = 80)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false, length = 30)
    private InventoryItemType itemType = InventoryItemType.INGREDIENT;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_unit", nullable = false, length = 30)
    private InventoryUnit baseUnit = InventoryUnit.EACH;

    @Column(name = "barcode", length = 80)
    private String barcode;

    @Column(name = "supplier_name", length = 150)
    private String supplierName;

    @Column(name = "supplier_sku", length = 100)
    private String supplierSku;

    @Column(name = "cost_per_unit", nullable = false, precision = 19, scale = 4)
    private BigDecimal costPerUnit = BigDecimal.ZERO;

    @Column(name = "reorder_point", precision = 12, scale = 3)
    private BigDecimal reorderPoint;

    @Column(name = "par_level", precision = 12, scale = 3)
    private BigDecimal parLevel;

    @Column(name = "track_inventory", nullable = false)
    private boolean trackInventory = true;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "storage_notes", columnDefinition = "text")
    private String storageNotes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_items_created_by_user")
    )
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "updated_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_items_updated_by_user")
    )
    private User updatedByUser;

    @OneToMany(mappedBy = "inventoryItem")
    private List<InventoryLevel> levels = new ArrayList<>();

    @OneToMany(mappedBy = "inventoryItem")
    private List<InventoryMovement> movements = new ArrayList<>();

    @OneToMany(mappedBy = "inventoryItem")
    private List<InventoryCountLine> countLines = new ArrayList<>();

    @OneToMany(mappedBy = "inventoryItem")
    private List<RecipeComponent> recipeComponents = new ArrayList<>();

    @Override
    protected void normalizeFields() {
        code = NormalizationUtils.normalizeCode(code == null ? name : code, 80);
        name = NormalizationUtils.normalize(name);
        description = NormalizationUtils.normalize(description);
        barcode = NormalizationUtils.normalizeUpper(barcode);
        supplierName = NormalizationUtils.normalize(supplierName);
        supplierSku = NormalizationUtils.normalizeUpper(supplierSku);
        storageNotes = NormalizationUtils.normalize(storageNotes);
        costPerUnit = defaultMoney(costPerUnit);
        reorderPoint = defaultQuantity(reorderPoint);
        parLevel = defaultQuantity(parLevel);
    }

    @Override
    protected void validateState() {
        validateNonNegative(costPerUnit, "costPerUnit");
        validateNonNegative(reorderPoint, "reorderPoint");
        validateNonNegative(parLevel, "parLevel");
    }

    private BigDecimal defaultMoney(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal defaultQuantity(BigDecimal value) {
        return value == null ? null : value;
    }

    private void validateNonNegative(BigDecimal value, String fieldName) {
        if (value != null && value.signum() < 0) {
            throw new IllegalStateException(fieldName + " must not be negative");
        }
    }
}
