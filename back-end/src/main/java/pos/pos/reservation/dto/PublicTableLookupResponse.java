package pos.pos.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.tables.enums.TableStatus;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicTableLookupResponse {

    private UUID restaurantId;
    private String restaurantName;
    private String restaurantSlug;
    private UUID branchId;
    private String branchName;
    private String branchCode;
    private UUID tableId;
    private String tableNumber;
    private String tableName;
    private String floor;
    private Integer capacity;
    private Integer effectiveCapacity;
    private TableStatus status;
    private Boolean active;
}
