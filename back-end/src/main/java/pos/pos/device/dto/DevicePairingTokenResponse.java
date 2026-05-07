package pos.pos.device.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DevicePairingTokenResponse {

    private UUID id;
    private UUID deviceId;
    private String state;
    private Boolean active;
    private String pairingToken;
    private OffsetDateTime expiresAt;
    private OffsetDateTime usedAt;
    private OffsetDateTime revokedAt;
    private String requestedIp;
    private OffsetDateTime createdAt;
    private UUID createdBy;
    private String createdByDisplayName;
}
