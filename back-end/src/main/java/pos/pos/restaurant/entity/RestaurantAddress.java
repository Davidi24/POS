package pos.pos.restaurant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import pos.pos.restaurant.enums.AddressType;
import pos.pos.utils.NormalizationUtils;

@Entity
@Table(
        name = "`restaurant-addresses`",
        indexes = {
                @Index(name = "idx_restaurant_addresses_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_restaurant_addresses_type", columnList = "address_type"),
                @Index(name = "idx_restaurant_addresses_is_primary", columnList = "is_primary"),
                @Index(name = "idx_restaurant_addresses_created_by", columnList = "created_by"),
                @Index(name = "idx_restaurant_addresses_updated_by", columnList = "updated_by"),
                @Index(name = "idx_restaurant_addresses_deleted_at", columnList = "deleted_at")
        }
)
@Check(constraints = """
        char_length(btrim(country)) > 0
        AND char_length(btrim(city)) > 0
        AND char_length(btrim(street_line_1)) > 0
        AND address_type IN ('LEGAL', 'BILLING', 'HEAD_OFFICE', 'SHIPPING', 'PHYSICAL')
        """)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class RestaurantAddress extends AbstractAuditedSoftDeleteEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_restaurant_addresses_restaurant")
    )
    private Restaurant restaurant;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 30)
    private AddressType addressType;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "street_line_1", nullable = false, length = 255)
    private String streetLine1;

    @Column(name = "street_line_2", length = 255)
    private String streetLine2;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary = false;

    @Override
    protected void normalizeFields() {
        country = NormalizationUtils.normalize(country);
        city = NormalizationUtils.normalize(city);
        postalCode = NormalizationUtils.normalize(postalCode);
        streetLine1 = NormalizationUtils.normalize(streetLine1);
        streetLine2 = NormalizationUtils.normalize(streetLine2);
    }
}
