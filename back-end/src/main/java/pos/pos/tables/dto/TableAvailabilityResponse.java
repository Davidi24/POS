package pos.pos.tables.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.tables.enums.TableStatus;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableAvailabilityResponse {

    private UUID tableId;
    private String tableNumber;
    private String name;
    private String floor;
    private Integer capacity;
    private Integer effectiveCapacity;
    private TableStatus status;
    private Boolean active;
    private UUID mergedIntoTableId;
    private List<UUID> mergedTableIds;
    private Boolean operationallyAvailable;
    private Boolean availableForRequestedWindow;
    private String blockingReason;
    private List<UUID> overlappingReservationIds;
}
