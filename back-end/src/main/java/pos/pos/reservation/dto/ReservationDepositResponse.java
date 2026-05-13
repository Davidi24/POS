package pos.pos.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.reservation.enums.ReservationDepositStatus;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationDepositResponse {

    private UUID reservationId;
    private Boolean depositRequired;
    private BigDecimal depositAmount;
    private ReservationDepositStatus depositStatus;
}
