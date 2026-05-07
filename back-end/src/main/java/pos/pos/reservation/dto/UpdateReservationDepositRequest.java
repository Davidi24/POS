package pos.pos.reservation.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReservationDepositRequest {

    private Boolean depositRequired;

    @DecimalMin(value = "0.0", inclusive = true, message = "depositAmount must not be negative")
    private BigDecimal depositAmount;
}
