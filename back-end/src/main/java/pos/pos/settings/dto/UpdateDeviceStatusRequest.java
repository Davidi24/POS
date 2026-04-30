package pos.pos.settings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateDeviceStatusRequest {

    @NotBlank(message = "status is required")
    private String status;

    @NotNull(message = "active is required")
    private Boolean active;

    @NotNull(message = "online is required")
    private Boolean online;
}
