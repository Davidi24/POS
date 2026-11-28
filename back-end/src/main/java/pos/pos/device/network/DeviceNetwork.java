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

/** One of a branch's networks: its address as [NetworkAddress] writes it, where it came from, when a device last used it. */
@Entity
@Table(name = "device_networks")
@Getter
@Setter
@NoArgsConstructor
public class DeviceNetwork {

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "branch_id", nullable = false, columnDefinition = "uuid")
    private UUID branchId;

    @Column(name = "address", nullable = false, length = 64)
    private String address;

    @Column(name = "learned_from_device_id", columnDefinition = "uuid")
    private UUID learnedFromDeviceId;

    @Column(name = "learned_from_name", length = 150)
    private String learnedFromName;

    @Column(name = "added_by", columnDefinition = "uuid")
    private UUID addedBy;

    @Column(name = "added_at", nullable = false)
    private OffsetDateTime addedAt;

    @Column(name = "last_seen_at")
    private OffsetDateTime lastSeenAt;
}
