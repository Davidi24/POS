package pos.pos.tables.entity;

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
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.utils.NormalizationUtils;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(
        name = "floor_layouts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_floor_layouts_branch_floor",
                        columnNames = {"branch_id", "floor_name"}
                )
        },
        indexes = {
                @Index(
                        name = "idx_floor_layouts_restaurant_id",
                        columnList = "restaurant_id"
                ),
                @Index(
                        name = "idx_floor_layouts_branch_id",
                        columnList = "branch_id"
                )
        }
)
@Check(constraints = """
        char_length(btrim(floor_name)) > 0
        AND plan_scale >= 0.25
        AND plan_scale <= 4.00
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class FloorLayout extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_floor_layouts_restaurant")
    )
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "branch_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_floor_layouts_branch")
    )
    private Branch branch;

    @Column(name = "floor_name", nullable = false, length = 50)
    private String floorName;

    @Column(name = "plan_image_key", length = 512)
    private String planImageKey;

    @Column(
            name = "plan_offset_x",
            nullable = false,
            precision = 10,
            scale = 4
    )
    private BigDecimal planOffsetX = BigDecimal.ZERO;

    @Column(
            name = "plan_offset_y",
            nullable = false,
            precision = 10,
            scale = 4
    )
    private BigDecimal planOffsetY = BigDecimal.ZERO;

    @Column(
            name = "plan_scale",
            nullable = false,
            precision = 6,
            scale = 4
    )
    private BigDecimal planScale = BigDecimal.ONE;

    @Override
    protected void normalizeFields() {
        floorName = NormalizationUtils.normalize(floorName);
        planImageKey = NormalizationUtils.normalize(planImageKey);

        if (planOffsetX == null) {
            planOffsetX = BigDecimal.ZERO;
        }

        if (planOffsetY == null) {
            planOffsetY = BigDecimal.ZERO;
        }

        if (planScale == null) {
            planScale = BigDecimal.ONE;
        }
    }

    @Override
    protected void validateState() {
        if (floorName == null || floorName.isBlank()) {
            throw new IllegalStateException("floorName is required");
        }

        if (
                planScale.compareTo(new BigDecimal("0.25")) < 0
                        || planScale.compareTo(new BigDecimal("4.00")) > 0
        ) {
            throw new IllegalStateException(
                    "planScale must be between 0.25 and 4.00"
            );
        }

        if (
                restaurant != null
                        && branch != null
                        && branch.getRestaurant() != null
                        && !Objects.equals(
                                restaurant.getId(),
                                branch.getRestaurant().getId()
                        )
        ) {
            throw new IllegalStateException(
                    "floor layout branch must belong to the same restaurant"
            );
        }
    }
}