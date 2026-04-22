package pos.pos.settings.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.common.entity.AbstractTimestampedEntity;

@Entity
@Table(
        name = "\"settings-order-rules\"",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_settings_order_rules_settings_id", columnNames = "settings_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true, onlyExplicitlyIncluded = true)
public class SettingsOrderRule extends AbstractTimestampedEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "settings_id",
            nullable = false,
            unique = true,
            columnDefinition = "uuid",
            foreignKey = @ForeignKey(name = "fk_settings_order_rules_settings")
    )
    private Settings settings;

    @Column(name = "auto_fire_to_kitchen", nullable = false)
    private boolean autoFireToKitchen = false;

    @Column(name = "allow_item_void", nullable = false)
    private boolean allowItemVoid = true;

    @Column(name = "allow_discount_without_manager", nullable = false)
    private boolean allowDiscountWithoutManager = false;

    @Column(name = "allow_backdated_orders", nullable = false)
    private boolean allowBackdatedOrders = false;

    @Column(name = "require_reason_for_void", nullable = false)
    private boolean requireReasonForVoid = true;

    @Column(name = "require_reason_for_discount", nullable = false)
    private boolean requireReasonForDiscount = true;

    @Column(name = "merge_orders_enabled", nullable = false)
    private boolean mergeOrdersEnabled = true;

    @Column(name = "transfer_orders_enabled", nullable = false)
    private boolean transferOrdersEnabled = true;

    @Column(name = "reopen_closed_orders_enabled", nullable = false)
    private boolean reopenClosedOrdersEnabled = false;
}
