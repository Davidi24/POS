package pos.pos.menu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;

/**
 * Represents a selectable option inside an option group.
 *
 * An option item is an individual modifier choice,
 * such as Bacon, Cheese, Extra Sauce etc.
 *
 * An option item may add an additional price.
 */

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(
        name = "`option-items`",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_option_items_group_name", columnNames = {"option_group_id", "name"})
        },
        indexes = {
                @Index(name = "idx_option_items_option_group_id", columnList = "option_group_id")
        }
)
@Check(constraints = """
        char_length(btrim(name)) > 0
        AND (code IS NULL OR char_length(btrim(code)) > 0)
        AND display_order >= 0
        """)
public class OptionItem extends BaseAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "option_group_id", nullable = false)
    private OptionGroup optionGroup;

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "price_delta", precision = 19, scale = 2)
    private BigDecimal priceDelta;

    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Override
    protected void normalizeFields() {
        code = normalizeCode(code);
        name = NormalizationUtils.normalize(name);
    }

    @Override
    protected void validateState() {
        if (displayOrder != null && displayOrder < 0) {
            throw new IllegalStateException("displayOrder must be greater than or equal to zero");
        }
    }

    private String normalizeCode(String value) {
        String normalized = NormalizationUtils.normalizeUpper(value);
        if (normalized == null) {
            return null;
        }

        String sanitized = normalized
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");

        return sanitized.isEmpty() ? null : sanitized;
    }
}
