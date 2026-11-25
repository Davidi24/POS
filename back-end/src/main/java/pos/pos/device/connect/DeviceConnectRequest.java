package pos.pos.device.connect;

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
 * A till or tablet's code typed on another device, waiting for the person who made the code to agree (user, 2026-11-25).
 * WAITING → AGREED (the new device then takes the connection: DONE) or REFUSED; CANCELLED by the new device. Past
 * [expiresAt] a WAITING or AGREED request counts as ended.
 */
@Entity
@Table(name = "device_connect_requests")
@Getter
@Setter
@NoArgsConstructor
public class DeviceConnectRequest {

    public static final String WAITING = "WAITING";
    public static final String AGREED = "AGREED";
    public static final String REFUSED = "REFUSED";
    public static final String CANCELLED = "CANCELLED";
    public static final String DONE = "DONE";
    /** Never stored: a WAITING or AGREED request past its time. */
    public static final String EXPIRED = "EXPIRED";

    @Id
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "restaurant_id", nullable = false, columnDefinition = "uuid")
    private UUID restaurantId;

    @Column(name = "device_id", nullable = false, columnDefinition = "uuid")
    private UUID deviceId;

    @Column(name = "pairing_token_id", nullable = false, columnDefinition = "uuid")
    private UUID pairingTokenId;

    @Column(name = "requested_by", nullable = false, columnDefinition = "uuid")
    private UUID requestedBy;

    @Column(name = "code_made_by", columnDefinition = "uuid")
    private UUID codeMadeBy;

    @Column(name = "asking_device_name", length = 150)
    private String askingDeviceName;

    @Column(name = "asking_device_system", length = 80)
    private String askingDeviceSystem;

    @Column(name = "asking_address", length = 64)
    private String askingAddress;

    @Column(name = "claim_hash", nullable = false, length = 128)
    private String claimHash;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "done_at")
    private OffsetDateTime doneAt;

    /** What the request is now: its status, or EXPIRED when it was still open past its time. */
    public String stateAt(OffsetDateTime now) {
        boolean open = WAITING.equals(status) || AGREED.equals(status);
        return open && !expiresAt.isAfter(now) ? EXPIRED : status;
    }
}
