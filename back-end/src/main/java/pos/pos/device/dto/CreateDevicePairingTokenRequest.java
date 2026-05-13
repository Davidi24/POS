package pos.pos.device.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
public class CreateDevicePairingTokenRequest {

    @Min(value = 1, message = "ttlMinutes must be greater than zero")
    private Integer ttlMinutes;

    @Size(max = 45, message = "requestedIp must be at most 45 characters")
    private String requestedIp;
}
