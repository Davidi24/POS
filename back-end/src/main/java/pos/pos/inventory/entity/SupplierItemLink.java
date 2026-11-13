package pos.pos.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.util.UUID;

/** A stock item you buy from a supplier: their code for it and their price per unit of the item. */
@Entity
@Table(name = "supplier_items")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class SupplierItemLink extends AbstractTimestampedEntity {

    @Column(name = "supplier_id", nullable = false, columnDefinition = "uuid")
    private UUID supplierId;

    @Column(name = "inventory_item_id", nullable = false, columnDefinition = "uuid")
    private UUID inventoryItemId;

    @Column(name = "code", length = 100)
    private String code;

    @Column(name = "price", precision = 19, scale = 4)
    private BigDecimal price;

    @Override
    protected void normalizeFields() {
        code = NormalizationUtils.normalize(code);
    }
}
