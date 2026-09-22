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


    //Duhet me e pa
    @Column(name = "barcode", length = 80)
    private String barcode;

    @Column(name = "supplier_name", length = 150)
    private String supplierName;


    /**
     *The supplier's own product code for this item (their SKU, not ours).
     * */
    @Column(name = "supplier_sku", length = 100)
    private String supplierSku;


    @Column(name = "cost_per_unit", nullable = false, precision = 19, scale = 4)
    private BigDecimal costPerUnit = BigDecimal.ZERO;

    /**
     * Stock level when it should be reloaded.
     *
     */
    @Column(name = "reorder_point", precision = 12, scale = 3)
    private BigDecimal reorderPoint;

    /**
     *	The target amount you want to have in stock after reordering (the "ideal" stock level).
     *
     * */

    @Column(name = "par_level", precision = 12, scale = 3)
    private BigDecimal parLevel;

    /**
     * Turns stock tracking on/off for this item.
     * If off, the system won't enforce or watch stock levels for it
     * (e.g. for items you don't want to bother counting napkins, toothpicks...).
     *
     */
    @Column(name = "track_inventory", nullable = false)
    private boolean trackInventory = true;

    /**
     * 	Whether this item is currently in use. Turned off = discontinued, but not deleted.
     */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;



    @Column(name = "storage_notes", columnDefinition = "text")
    private String storageNotes;


    /**
     * Which user created this item record. Filled automatically, can't be set manually.(Logged Account)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_items_created_by_user")
    )
    private User createdByUser;

    /**
     * Same as created
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "updated_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_inventory_items_updated_by_user")
    )
    private User updatedByUser;

    /**
     * These four aren't actual columns in the database — nothing is stored on the InventoryItem table for them. They're just shortcuts to look things up from the item's side, like a quick "show me everything connected to this item" view.
     *
     * Real-life scenario — imagine you're looking at "Chicken Breast" in the app and want a full picture:
     *
     * levels → "Where do we currently have chicken breast, and how much?" → shows: 5kg at Kitchen, 12kg at Walk-in Freezer.
     * movements → "What has happened to chicken breast recently?" → shows: delivery of 20kg Monday, 2kg used for orders Tuesday, 0.5kg wasted Wednesday.
     * countLines → "Was chicken breast ever counted, and how'd it go?" → shows: last month's count found 0.3kg less than expected.
     * recipeComponents → "Which recipes use chicken breast?" → shows: Chicken Parmesan, Caesar Salad, Chicken Curry.
     */


    /**
     * 	List of stock balances for this item across all locations (how much is where).
     * 	Read-only link, not stored on this table itself.
     */
    @OneToMany(mappedBy = "inventoryItem")
    private List<InventoryLevel> levels = new ArrayList<>();

    /**
     * 	List of every stock change event involving this item. Read-only link.
     */
    @OneToMany(mappedBy = "inventoryItem")
    private List<InventoryMovement> movements = new ArrayList<>();

    /**
     * 	List of every stock-take result involving this item. Read-only link.
     */
    @OneToMany(mappedBy = "inventoryItem")
    private List<InventoryCountLine> countLines = new ArrayList<>();

    /**
     * List of every recipe that uses this item as an ingredient. Read-only link.
     */
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
