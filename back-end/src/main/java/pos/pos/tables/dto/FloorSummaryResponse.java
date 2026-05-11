package pos.pos.tables.dto;

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
public class FloorSummaryResponse {

    private String name;
    private Integer tableCount;
    private Integer positionedTableCount;
}
