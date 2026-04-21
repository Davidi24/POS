package pos.pos.restaurant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.utils.NormalizationUtils;

@Entity
@Table(
        name = "`restaurant-branding`",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_restaurant_branding_restaurant", columnNames = "restaurant_id")
        },
        indexes = {
                @Index(name = "idx_restaurant_branding_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_restaurant_branding_created_by", columnList = "created_by"),
                @Index(name = "idx_restaurant_branding_updated_by", columnList = "updated_by"),
                @Index(name = "idx_restaurant_branding_deleted_at", columnList = "deleted_at")
        }
)
@Check(constraints = """
        (primary_color IS NULL OR char_length(btrim(primary_color)) > 0)
        AND (secondary_color IS NULL OR char_length(btrim(secondary_color)) > 0)
        """)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class RestaurantBranding extends AbstractAuditedSoftDeleteEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_restaurant_branding_restaurant")
    )
    private Restaurant restaurant;

    @Column(name = "logo_url", columnDefinition = "text")
    private String logoUrl;

    @Column(name = "primary_color", length = 20)
    private String primaryColor;

    @Column(name = "secondary_color", length = 20)
    private String secondaryColor;

    @Column(name = "receipt_header", columnDefinition = "text")
    private String receiptHeader;

    @Column(name = "receipt_footer", columnDefinition = "text")
    private String receiptFooter;

    @Override
    protected void normalizeFields() {
        logoUrl = NormalizationUtils.normalize(logoUrl);
        primaryColor = NormalizationUtils.normalize(primaryColor);
        secondaryColor = NormalizationUtils.normalize(secondaryColor);
        receiptHeader = NormalizationUtils.normalize(receiptHeader);
        receiptFooter = NormalizationUtils.normalize(receiptFooter);
    }
}
