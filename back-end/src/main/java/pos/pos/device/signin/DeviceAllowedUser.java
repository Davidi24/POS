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

/** A person who may sign in on a device (V140). */
@Entity
@Table(name = "device_allowed_users")
@Getter
@Setter
@NoArgsConstructor
public class DeviceAllowedUser {

    @Id
    @Column(name = "id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "device_id", nullable = false, columnDefinition = "uuid")
    private UUID deviceId;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "created_at", nullable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    public DeviceAllowedUser(UUID deviceId, UUID userId, OffsetDateTime createdAt) {
        this.id = UUID.randomUUID();
        this.deviceId = deviceId;
        this.userId = userId;
        this.createdAt = createdAt;
    }
}
