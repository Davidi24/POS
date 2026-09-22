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
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.menu.util.MenuCodeNormalizer;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.utils.NormalizationUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a restaurant menu.
 *
 * A menu is the top-level container for organizing food or drink offerings,
 * such as Breakfast, Dinner, or Drinks menus.
 *
 * A menu contains one or more menu sections.
 */

@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(
        name = "menus",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_menus_restaurant_code", columnNames = {"restaurant_id", "code"})
        },
        indexes = {
                @Index(name = "idx_menus_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_menus_created_by", columnList = "created_by"),
                @Index(name = "idx_menus_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = """
        char_length(btrim(code)) > 0
        AND char_length(btrim(name)) > 0
        AND display_order >= 0
        AND (
            (available_from_date IS NULL AND available_until_date IS NULL)
            OR (
                available_from_date IS NOT NULL
                AND available_until_date IS NOT NULL
                AND available_from_date <= available_until_date
            )
        )
        """)
public class Menu extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false) //orders in which menus are shown
    private Integer displayOrder = 0;

    @Column(name = "all_filter_position")
    private Integer allFilterPosition;

    @Column(name = "available_from")
    private LocalTime availableFrom;

    @Column(name = "available_until")
    private LocalTime availableUntil;

    @Column(name = "available_from_date")
    private LocalDate availableFromDate;

    @Column(name = "available_until_date")
    private LocalDate availableUntilDate;

    @Column(name = "color", length = 20)
    private String color;

    @OneToMany(mappedBy = "menu")
    private List<MenuSection> sections = new ArrayList<>();

    @Override
    protected void normalizeFields() {
        code = MenuCodeNormalizer.normalize(code == null ? name : code);
        name = NormalizationUtils.normalize(name);
        description = NormalizationUtils.normalize(description);
    }

    @Override
    protected void validateState() {
        if (displayOrder != null && displayOrder < 0) {
            throw new IllegalStateException("displayOrder must be greater than or equal to zero");
        }
        if ((availableFromDate == null) != (availableUntilDate == null)) {
            throw new IllegalStateException("availableFromDate and availableUntilDate must both be set or both be empty");
        }
        if (availableFromDate != null && availableFromDate.isAfter(availableUntilDate)) {
            throw new IllegalStateException("availableFromDate must not be after availableUntilDate");
        }
    }

    public boolean isAvailableOn(LocalDate date) {
        return date != null
                && (availableFromDate == null || !date.isBefore(availableFromDate))
                && (availableUntilDate == null || !date.isAfter(availableUntilDate));
    }
}
