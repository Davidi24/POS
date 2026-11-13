package pos.pos.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.inventory.enums.InventoryKindScope;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;

/**
 * A kind of stock item or place that the restaurant manages. Either one of its own kinds (a name; items and places use it
 * through type CUSTOM + kind), or the mark that a built-in kind was removed (builtinCode set, deletedAt filled in).
 */
@Entity
@Table(name = "inventory_kinds")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class InventoryKind extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false, columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_inventory_kinds_restaurant"))
    private Restaurant restaurant;

    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", nullable = false, length = 10)
    private InventoryKindScope appliesTo;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "builtin_code", length = 30)
    private String builtinCode;

    @Column(name = "deleted_at", columnDefinition = "timestamptz")
    private OffsetDateTime deletedAt;

    @Override
    protected void normalizeFields() {
        name = NormalizationUtils.normalize(name);
    }
}
