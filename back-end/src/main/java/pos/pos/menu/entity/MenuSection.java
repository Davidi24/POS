package pos.pos.menu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.utils.NormalizationUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a section within a menu.
 *
 * A menu section groups related menu items into categories,
 * such as Appetizers, Burgers, Desserts, Pasta ...
 *
 * A menu section belongs to one menu
 * and contains one or more menu items.
 */

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(
        name = "`menu-sections`",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_menu_sections_menu_name", columnNames = {"menu_id", "name"})
        },
        indexes = {
                @Index(name = "idx_menu_sections_menu_id", columnList = "menu_id")
        }
)
@Check(constraints = """
        char_length(btrim(name)) > 0
        AND display_order >= 0
        """)
public class MenuSection extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "menu_id", nullable = false)
    private Menu menu;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "section")
    private List<MenuItem> items = new ArrayList<>();

    @Override
    protected void normalizeFields() {
        name = NormalizationUtils.normalize(name);
        description = NormalizationUtils.normalize(description);
    }

    @Override
    protected void validateState() {
        if (displayOrder != null && displayOrder < 0) {
            throw new IllegalStateException("displayOrder must be greater than or equal to zero");
        }
    }
}
