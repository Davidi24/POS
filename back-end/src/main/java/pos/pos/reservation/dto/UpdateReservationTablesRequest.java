package pos.pos.reservation.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotEmpty;
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
public class UpdateReservationTablesRequest {

    @NotEmpty(message = "tableIds is required")
    @Size(max = 50, message = "tableIds can have at most 50 values")
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") UUID> tableIds;

    private UUID primaryTableId;
}
