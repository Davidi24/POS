package pos.pos.kds.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.device.enums.DeviceStatus;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KdsAssignableDeviceResponse {

    private UUID deviceId;
    private String deviceCode;
    private String deviceName;
    private DeviceStatus status;
    private Boolean active;
    private Boolean online;
    private UUID assignedStationId;
    private String assignedStationCode;
    private String assignedStationName;
}
