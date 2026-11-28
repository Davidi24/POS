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

/** A device turned away for being on another network (kept a week): connecting it (CONNECT) or a PIN sign-in (PIN). */
@Entity
@Table(name = "device_network_refusals")
@Getter
@Setter
@NoArgsConstructor
public class DeviceNetworkRefusal {

    public static final String CONNECT = "CONNECT";
    public static final String PIN = "PIN";

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "branch_id", nullable = false, columnDefinition = "uuid")
    private UUID branchId;

    @Column(name = "address", nullable = false, length = 64)
    private String address;

    @Column(name = "device_id", columnDefinition = "uuid")
    private UUID deviceId;

    @Column(name = "device_name", length = 150)
    private String deviceName;

    @Column(name = "kind", nullable = false, length = 20)
    private String kind;

    @Column(name = "refused_at", nullable = false)
    private OffsetDateTime refusedAt;
}
