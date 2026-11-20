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
public class OrderMergeRequest {

    @NotNull(message = "sourceOrderId is required")
    private UUID sourceOrderId;

    @Size(max = 1000, message = "note must be at most 1000 characters")
    private String note;
}
