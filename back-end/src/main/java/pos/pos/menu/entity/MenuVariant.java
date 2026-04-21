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
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(
        name = "menu-variants",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_menu_variants_menu_item_name", columnNames = {"menu_item_id", "name"})
        },
        indexes = {
                @Index(name = "idx_menu_variants_menu_item_id", columnList = "menu_item_id")
        }
)
public class MenuVariant extends BaseAuditEntity {

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
}