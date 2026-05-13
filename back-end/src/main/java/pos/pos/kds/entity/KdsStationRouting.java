package pos.pos.kds.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractTimestampedEntity;
import pos.pos.kds.enums.KdsPriority;
import pos.pos.menu.entity.MenuItem;
import pos.pos.utils.NormalizationUtils;

import java.util.Objects;

@Entity
@Table(
        name = "kds_station_routings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_kds_station_routings_station_menu_item", columnNames = {"station_id", "menu_item_id"})
        },
        indexes = {
                @Index(name = "idx_kds_station_routings_station_id", columnList = "station_id"),
                @Index(name = "idx_kds_station_routings_menu_item_id", columnList = "menu_item_id")
        }
)
@Check(constraints = """
        priority IN ('NORMAL', 'RUSH', 'VIP', 'HOLD_FIRE')
        AND display_order >= 0
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class KdsStationRouting extends AbstractTimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "station_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_kds_station_routings_station")
    )
    private KdsStation station;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "menu_item_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_kds_station_routings_menu_item")
    )
    private MenuItem menuItem;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 30)
    private KdsPriority priority = KdsPriority.NORMAL;

    @Column(name = "course_label", length = 50)
    private String courseLabel;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Override
    protected void normalizeFields() {
        courseLabel = NormalizationUtils.normalize(courseLabel);
    }

    @Override
    protected void validateState() {
        if (displayOrder < 0) {
            throw new IllegalStateException("displayOrder must not be negative");
        }

        if (station != null
                && menuItem != null
                && station.getRestaurant() != null
                && menuItem.getSection() != null
                && menuItem.getSection().getMenu() != null
                && menuItem.getSection().getMenu().getRestaurant() != null) {
            if (!Objects.equals(
                    station.getRestaurant().getId(),
                    menuItem.getSection().getMenu().getRestaurant().getId()
            )) {
                throw new IllegalStateException("kds routing menu item must belong to the same restaurant");
            }
        }
    }
}
