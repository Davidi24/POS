package pos.pos.device.network;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A branch's switch "Tills only on the restaurant's network" (user, 2026-11-24). The row is made the first time the branch
 * gets a network (by itself, when its first till, tablet or screen is connected) or when someone changes it; once it's there
 * nothing is learned by itself any more.
 */
@Entity
@Table(name = "device_network_rules")
@Getter
@Setter
@NoArgsConstructor
public class DeviceNetworkRule {

    @Id
    @Column(name = "branch_id", columnDefinition = "uuid")
    private UUID branchId;

    @Column(name = "restaurant_id", nullable = false, columnDefinition = "uuid")
    private UUID restaurantId;

    @Column(name = "only_on_network", nullable = false)
    private boolean onlyOnNetwork;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by", columnDefinition = "uuid")
    private UUID updatedBy;
}
