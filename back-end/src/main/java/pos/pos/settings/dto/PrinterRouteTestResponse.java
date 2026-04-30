package pos.pos.settings.dto;

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
public class PrinterRouteTestResponse {

    private UUID restaurantId;
    private UUID branchId;
    private Integer printerCount;
    private OffsetDateTime requestedAt;
    private String payload;
}
