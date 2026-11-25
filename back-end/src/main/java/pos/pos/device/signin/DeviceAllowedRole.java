package pos.pos.device.signin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/** A role whose people may sign in on a device (V140). */
@Entity
@Table(name = "device_allowed_roles")
@Getter
@Setter
@NoArgsConstructor
public class DeviceAllowedRole {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "device_id", nullable = false, columnDefinition = "uuid")
    private UUID deviceId;

    @Column(name = "role_id", nullable = false, columnDefinition = "uuid")
    private UUID roleId;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    public DeviceAllowedRole(UUID deviceId, UUID roleId, OffsetDateTime createdAt) {
        this.id = UUID.randomUUID();
        this.deviceId = deviceId;
        this.roleId = roleId;
        this.createdAt = createdAt;
    }
}
