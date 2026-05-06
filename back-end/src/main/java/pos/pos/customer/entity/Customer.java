package pos.pos.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractAuditedSoftDeleteEntity;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.utils.NormalizationUtils;

@Entity
@Table(
        name = "customers",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_customers_restaurant_code", columnNames = {"restaurant_id", "code"})
        },
        indexes = {
                @Index(name = "idx_customers_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_customers_email", columnList = "email"),
                @Index(name = "idx_customers_phone", columnList = "phone"),
                @Index(name = "idx_customers_deleted_at", columnList = "deleted_at"),
                @Index(name = "idx_customers_created_by", columnList = "created_by"),
                @Index(name = "idx_customers_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = """
        (code IS NULL OR char_length(btrim(code)) > 0)
        AND (
            first_name IS NOT NULL
            OR last_name IS NOT NULL
            OR email IS NOT NULL
            OR phone IS NOT NULL
        )
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class Customer extends AbstractAuditedSoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_customers_restaurant")
    )
    private Restaurant restaurant;

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Override
    protected void normalizeFields() {
        code = NormalizationUtils.normalizeCode(code);
        firstName = NormalizationUtils.normalize(firstName);
        lastName = NormalizationUtils.normalize(lastName);
        email = NormalizationUtils.normalizeLower(email);
        phone = NormalizationUtils.normalizePhone(phone);
        notes = NormalizationUtils.normalize(notes);
    }

    @Override
    protected void validateState() {
        if (firstName == null && lastName == null && email == null && phone == null) {
            throw new IllegalStateException("customer requires at least one identifying or contact field");
        }
    }
}
