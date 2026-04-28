package pos.pos.restaurant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractAuditedSoftDeleteEntity;
import pos.pos.utils.NormalizationUtils;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "`restaurant-tax-profiles`",
        indexes = {
                @Index(name = "idx_restaurant_tax_profiles_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_restaurant_tax_profiles_is_default", columnList = "is_default"),
                @Index(name = "idx_restaurant_tax_profiles_effective_from", columnList = "effective_from"),
                @Index(name = "idx_restaurant_tax_profiles_effective_to", columnList = "effective_to"),
                @Index(name = "idx_restaurant_tax_profiles_created_by", columnList = "created_by"),
                @Index(name = "idx_restaurant_tax_profiles_updated_by", columnList = "updated_by"),
                @Index(name = "idx_restaurant_tax_profiles_deleted_at", columnList = "deleted_at")
        }
)
@Check(constraints = """
        char_length(btrim(country)) > 0
        AND (effective_from IS NULL OR effective_to IS NULL OR effective_to > effective_from)
        """)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class RestaurantTaxProfile extends AbstractAuditedSoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_restaurant_tax_profiles_restaurant")
    )
    private Restaurant restaurant;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "fiscal_code", length = 100)
    private String fiscalCode;

    @Column(name = "tax_number", length = 100)
    private String taxNumber;

    @Column(name = "vat_number", length = 100)
    private String vatNumber;

    @Column(name = "tax_office", length = 150)
    private String taxOffice;

    // Only one active default profile is allowed per restaurant; the DB enforces it with a partial unique index.
    @Column(name = "is_default", nullable = false)
    private boolean isDefault = false;

    @Column(name = "effective_from", columnDefinition = "timestamptz")
    private OffsetDateTime effectiveFrom;

    @Column(name = "effective_to", columnDefinition = "timestamptz")
    private OffsetDateTime effectiveTo;

    @Override
    protected void normalizeFields() {
        country = NormalizationUtils.normalize(country);
        fiscalCode = NormalizationUtils.normalize(fiscalCode);
        taxNumber = NormalizationUtils.normalize(taxNumber);
        vatNumber = NormalizationUtils.normalize(vatNumber);
        taxOffice = NormalizationUtils.normalize(taxOffice);
    }
}
