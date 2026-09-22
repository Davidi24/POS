package pos.pos.kds.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.kds.enums.KdsStationType;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KdsStationResponse {

    private UUID id;
    private UUID restaurantId;
    private UUID branchId;
    private String branchName;
    private UUID deviceId;
    private String deviceCode;
    private String deviceName;
    private String code;
    private String name;
    private KdsStationType stationType;
    private Integer displayOrder;
    private Boolean active;
    private Boolean acceptsScheduledOrders;
    private String screenLabel;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
    private List<KdsStationRoutingResponse> routings;
}
