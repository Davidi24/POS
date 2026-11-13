package pos.pos.order.dto;

import jakarta.validation.constraints.Size;
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
public class OrderCloseUnpaidRequest {

    // Staff write it: there are no ready-made reasons (user, 2026-11-01).
    @jakarta.validation.constraints.NotBlank(message = "reason is required")
    @Size(max = 2000, message = "reason can have at most 2000 characters")
    private String reason;
}
