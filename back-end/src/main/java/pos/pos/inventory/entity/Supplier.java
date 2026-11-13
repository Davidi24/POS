package pos.pos.inventory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Someone the restaurant buys from (Inventory workspace → Suppliers). */
@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Supplier extends AbstractAuditedEntity {

    @Column(name = "restaurant_id", nullable = false, columnDefinition = "uuid")
    private UUID restaurantId;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "contact_name", length = 150)
    private String contactName;

    @Column(name = "phone", length = 40)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "address", length = 240)
    private String address;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at", columnDefinition = "timestamptz")
    private OffsetDateTime deletedAt;

    @Override
    protected void normalizeFields() {
        name = name == null ? null : NormalizationUtils.normalize(name).replaceAll("\\s+", " ");
        contactName = NormalizationUtils.normalize(contactName);
        phone = NormalizationUtils.normalize(phone);
        email = NormalizationUtils.normalize(email);
        address = NormalizationUtils.normalize(address);
        notes = NormalizationUtils.normalize(notes);
    }
}
