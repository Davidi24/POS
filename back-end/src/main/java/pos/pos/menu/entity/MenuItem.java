package pos.pos.menu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(
        name = "menu-items",
        indexes = {
                @Index(name = "idx_menu_items_section_id", columnList = "section_id"),
                @Index(name = "idx_menu_items_sku", columnList = "sku")
        }
)
public class MenuItem extends BaseAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "section_id", nullable = false)
    private MenuSection section;

    @Column(name = "sku", length = 80)
    private String sku;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "base_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal basePrice;

    @Column(name = "image_url", columnDefinition = "text")
    private String imageUrl;

    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @OneToMany(mappedBy = "menuItem")
    private List<MenuVariant> variants = new ArrayList<>();

    @OneToMany(mappedBy = "menuItem")
    private List<MenuItemOptionGroup> optionGroups = new ArrayList<>();
}