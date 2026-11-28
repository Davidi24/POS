package pos.pos.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// An extra guests can order with a booking (from a special menu), e.g. "Birthday cake · €25 · order 48 h ahead".
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuestExtraChoice {
    private UUID menuItemId;
    private String name;
    private String description;
    private BigDecimal price;
    private String currency;
    private Integer orderBeforeHours;
    private List<String> occasionCodes;
}
