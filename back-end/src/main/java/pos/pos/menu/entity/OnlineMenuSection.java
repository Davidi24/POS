package pos.pos.menu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.utils.NormalizationUtils;

// A section of the restaurant's online menu (what customers see on the website). It holds no dishes of its own:
// a dish points at it through MenuItem.onlineSection while keeping its normal staff section.
@Entity
@Table(
        name = "online_menu_sections",
        indexes = @Index(name = "idx_online_menu_sections_restaurant_id", columnList = "restaurant_id")
)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class OnlineMenuSection extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false, columnDefinition = "uuid", foreignKey = @ForeignKey(name = "fk_online_menu_sections_restaurant"))
    private Restaurant restaurant;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Override
    protected void normalizeFields() {
        name = NormalizationUtils.normalize(name);
    }

    @Override
    protected void validateState() {
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("online menu section name is required");
        }
        if (displayOrder < 0) {
            throw new IllegalStateException("displayOrder must not be negative");
        }
    }
}
