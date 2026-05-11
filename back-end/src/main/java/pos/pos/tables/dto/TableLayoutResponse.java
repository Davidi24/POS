package pos.pos.tables.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableLayoutResponse {

    private UUID restaurantId;
    private UUID branchId;
    private List<FloorSummaryResponse> floors;
    private List<TableLayoutItemResponse> tables;
}
