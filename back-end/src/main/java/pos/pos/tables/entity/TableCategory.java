package pos.pos.tables.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.restaurant.entity.Branch;
import pos.pos.tables.enums.TableLocationType;
import pos.pos.utils.NormalizationUtils;

import java.util.ArrayList;
import java.util.List;

// TableCategory represents the table area or type, for example PATIO, BAR, MAIN_DINING, or PRIVATE_ROOM.
// It is used to group similar physical tables and store shared/default information like location, color, order, or special rules.
@Entity
@Table(
        name = "\"table-categories\"",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_table_categories_branch_code", columnNames = {"branch_id", "code"}),
                @UniqueConstraint(name = "uk_table_categories_branch_name", columnNames = {"branch_id", "name"})
        },
        indexes = {
                @Index(name = "idx_table_categories_branch_id", columnList = "branch_id")
        }
)
@Check(constraints = """
        char_length(btrim(code)) > 0
        AND char_length(btrim(name)) > 0
        AND default_capacity > 0
        AND display_order >= 0
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class TableCategory extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "branch_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_table_categories_branch")
    )
    private Branch branch;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "default_capacity", nullable = false)
    private int defaultCapacity = 2;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "location_type", length = 30)
    private TableLocationType locationType = TableLocationType.INDOOR;

    @Column(name = "color", length = 20)
    private String color;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = false)
    private List<RestaurantTable> tables = new ArrayList<>();

    @Override
    protected void normalizeFields() {
        code = NormalizationUtils.normalizeCode(code == null ? name : code);
        name = NormalizationUtils.normalize(name);
        description = NormalizationUtils.normalize(description);
        color = NormalizationUtils.normalizeUpper(color);
    }

    @Override
    protected void validateState() {
        if (defaultCapacity <= 0) {
            throw new IllegalStateException("defaultCapacity must be greater than zero");
        }

        if (displayOrder < 0) {
            throw new IllegalStateException("displayOrder must not be negative");
        }
    }
}

