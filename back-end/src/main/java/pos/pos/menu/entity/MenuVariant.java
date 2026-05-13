package pos.pos.menu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;

/**
 * Represents a variation of a menu item.
 *
 * A variant defines alternate versions of a menu item,
 * such as size, portion, or configuration,
 * and may adjust the item's base price.
 */

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(
        name = "`menu-variants`",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_menu_variants_menu_item_name", columnNames = {"menu_item_id", "name"})
        },
        indexes = {
                @Index(name = "idx_menu_variants_menu_item_id", columnList = "menu_item_id")
        }
)
@Check(constraints = """
        char_length(btrim(name)) > 0
        AND (sku IS NULL OR char_length(btrim(sku)) > 0)
        AND display_order >= 0
        """)
public class MenuVariant extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "sku", length = 80)
    private String sku;

    @Column(name = "price_delta", precision = 19, scale = 2)
    private BigDecimal priceDelta;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Override
    protected void normalizeFields() {
        name = NormalizationUtils.normalize(name);
        sku = NormalizationUtils.normalizeUpper(sku);
    }

    @Override
    protected void validateState() {
        if (displayOrder != null && displayOrder < 0) {
            throw new IllegalStateException("displayOrder must be greater than or equal to zero");
        }
    }
}
