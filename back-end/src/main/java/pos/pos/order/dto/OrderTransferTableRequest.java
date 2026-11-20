package pos.pos.order.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTransferTableRequest {

    @NotNull(message = "tableId is required")
    private UUID tableId;

    @Size(max = 1000, message = "note must be at most 1000 characters")
    private String note;
}
