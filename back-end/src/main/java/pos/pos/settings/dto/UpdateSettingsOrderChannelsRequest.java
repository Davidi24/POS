package pos.pos.settings.dto;

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
public class UpdateSettingsOrderChannelsRequest {

    @NotNull(message = "allowOpenTickets is required")
    private Boolean allowOpenTickets;

    @NotNull(message = "enableQrOrdering is required")
    private Boolean enableQrOrdering;

    @NotNull(message = "enableTakeaway is required")
    private Boolean enableTakeaway;

    @NotNull(message = "enableDelivery is required")
    private Boolean enableDelivery;
}
