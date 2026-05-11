package pos.pos.tables.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.tables.enums.TableShape;
import pos.pos.tables.enums.TableStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableResponse {

    private UUID id;
    private UUID restaurantId;
    private UUID branchId;
    private UUID categoryId;
    private String categoryCode;
    private String categoryName;
    private UUID mergedIntoTableId;
    private List<UUID> mergedTableIds;
    private String tableNumber;
    private String name;
    private Integer capacity;
    private Integer effectiveCapacity;
    private String floor;
    private BigDecimal positionX;
    private BigDecimal positionY;
    private TableShape shape;
    private TableStatus status;
    private Boolean active;
    private String qrCodeValue;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
