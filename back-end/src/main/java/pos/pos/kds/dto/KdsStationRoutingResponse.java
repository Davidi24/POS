package pos.pos.kds.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.kds.enums.KdsPriority;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KdsStationRoutingResponse {

    private UUID id;
    private UUID menuItemId;
    private String menuItemName;
    private Integer displayOrder;
    private KdsPriority priority;
    private String courseLabel;
    private Boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
