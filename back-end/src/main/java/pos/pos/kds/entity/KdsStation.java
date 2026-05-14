package pos.pos.kds.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;
import pos.pos.common.entity.AbstractAuditedEntity;
import pos.pos.device.entity.Device;
import pos.pos.device.enums.DeviceType;
import pos.pos.kds.enums.KdsStationType;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.user.entity.User;
import pos.pos.utils.NormalizationUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(
        name = "kds_stations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_kds_stations_branch_code", columnNames = {"branch_id", "code"}),
                @UniqueConstraint(name = "uk_kds_stations_device_id", columnNames = {"device_id"})
        },
        indexes = {
                @Index(name = "idx_kds_stations_restaurant_id", columnList = "restaurant_id"),
                @Index(name = "idx_kds_stations_branch_id", columnList = "branch_id"),
                @Index(name = "idx_kds_stations_device_id", columnList = "device_id"),
                @Index(name = "idx_kds_stations_station_type", columnList = "station_type"),
                @Index(name = "idx_kds_stations_created_by", columnList = "created_by"),
                @Index(name = "idx_kds_stations_updated_by", columnList = "updated_by")
        }
)
@Check(constraints = """
        char_length(btrim(code)) > 0
        AND char_length(btrim(name)) > 0
        AND station_type IN ('PREP', 'GRILL', 'FRY', 'GARDE_MANGER', 'BAR', 'DESSERT', 'EXPO', 'PACKING')
        AND display_order >= 0
        """)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class KdsStation extends AbstractAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "restaurant_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_kds_stations_restaurant")
    )
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "branch_id",
            nullable = false,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_kds_stations_branch")
    )
    private Branch branch;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "device_id",
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_kds_stations_device")
    )
    private Device device;

    @Column(name = "code", nullable = false, length = 80)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "station_type", nullable = false, length = 30)
    private KdsStationType stationType = KdsStationType.PREP;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "accepts_scheduled_orders", nullable = false)
    private boolean acceptsScheduledOrders = true;

    @Column(name = "screen_label", length = 80)
    private String screenLabel;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_kds_stations_created_by_user")
    )
    private User createdByUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "updated_by",
            columnDefinition = "uuid",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_kds_stations_updated_by_user")
    )
    private User updatedByUser;

    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, createdAt ASC")
    private List<KdsStationRouting> routings = new ArrayList<>();

    public void addRouting(KdsStationRouting routing) {
        if (routing == null) {
            return;
        }

        routings.add(routing);
        routing.setStation(this);
    }

    public void removeRouting(KdsStationRouting routing) {
        if (routing == null) {
            return;
        }

        routings.remove(routing);
        routing.setStation(null);
    }

    @Override
    protected void normalizeFields() {
        code = NormalizationUtils.normalizeCode(code == null ? name : code, 80);
        name = NormalizationUtils.normalize(name);
        screenLabel = NormalizationUtils.normalize(screenLabel);
        notes = NormalizationUtils.normalize(notes);
    }

    @Override
    protected void validateState() {
        if (displayOrder < 0) {
            throw new IllegalStateException("displayOrder must not be negative");
        }

        if (restaurant != null && branch != null && branch.getRestaurant() != null) {
            if (!Objects.equals(branch.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("kds station branch must belong to the same restaurant");
            }
        }

        if (device != null) {
            if (device.getDeviceType() != DeviceType.KDS) {
                throw new IllegalStateException("kds station device must use the KDS device type");
            }

            if (restaurant != null && device.getRestaurant() != null
                    && !Objects.equals(device.getRestaurant().getId(), restaurant.getId())) {
                throw new IllegalStateException("kds station device must belong to the same restaurant");
            }

            if (branch != null) {
                if (device.getBranch() == null) {
                    throw new IllegalStateException("kds station device must be assigned to the same branch");
                }
                if (!Objects.equals(device.getBranch().getId(), branch.getId())) {
                    throw new IllegalStateException("kds station device must belong to the same branch");
                }
            }
        }
    }
}
