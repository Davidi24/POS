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
public class TableMapResponse {

    private UUID restaurantId;
    private UUID branchId;
    private List<TableMapFloorResponse> floors;
}
