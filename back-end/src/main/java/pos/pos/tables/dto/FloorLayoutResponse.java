package pos.pos.tables.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloorLayoutResponse {

    private UUID id;
    private UUID restaurantId;
    private UUID branchId;

    private String floorName;

    private String planImageKey;
    private String planImageUrl;

    private BigDecimal planOffsetX;
    private BigDecimal planOffsetY;
    private BigDecimal planScale;

    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}