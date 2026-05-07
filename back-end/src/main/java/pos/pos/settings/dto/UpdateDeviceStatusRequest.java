package pos.pos.settings.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.device.enums.DeviceStatus;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDeviceStatusRequest {

    @NotNull(message = "status is required")
    private DeviceStatus status;

    @NotNull(message = "active is required")
    private Boolean active;

    @NotNull(message = "online is required")
    private Boolean online;
}
