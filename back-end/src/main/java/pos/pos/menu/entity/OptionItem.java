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
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(
        name = "option-items",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_option_items_group_name", columnNames = {"option_group_id", "name"})
        },
        indexes = {
                @Index(name = "idx_option_items_option_group_id", columnList = "option_group_id")
        }
)
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
}