package pos.pos.tables.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.tables.enums.TableShape;
import pos.pos.tables.enums.TableStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableLayoutItemResponse {

    private UUID tableId;
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
}
