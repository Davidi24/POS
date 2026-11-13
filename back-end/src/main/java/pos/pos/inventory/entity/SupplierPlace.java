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

import java.util.UUID;

/** One of a supplier's places you order from (a warehouse, a branch), with the weekdays they deliver from it. */
@Entity
@Table(name = "supplier_places")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class SupplierPlace extends AbstractTimestampedEntity {

    @Column(name = "supplier_id", nullable = false, columnDefinition = "uuid")
    private UUID supplierId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "address", length = 240)
    private String address;

    @Column(name = "phone", length = 40)
    private String phone;

    /** ISO weekdays (1 Monday … 7 Sunday), comma separated. */
    @Column(name = "delivery_days", length = 20)
    private String deliveryDays;

    @Override
    protected void normalizeFields() {
        name = NormalizationUtils.normalize(name);
        address = NormalizationUtils.normalize(address);
        phone = NormalizationUtils.normalize(phone);
    }
}
