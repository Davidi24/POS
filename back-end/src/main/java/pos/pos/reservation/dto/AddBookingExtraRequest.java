package pos.pos.reservation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class AddBookingExtraRequest {
    @NotNull(message = "menuItemId is required")
    private UUID menuItemId;

    @NotNull(message = "quantity is required")
    @Min(1)
    @Max(50)
    private Integer quantity;
}
