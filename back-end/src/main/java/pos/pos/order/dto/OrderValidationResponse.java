package pos.pos.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderValidationResponse {

    private boolean valid;
    private String suggestedOrderNumber;
    private List<String> errors;
    private List<String> warnings;
    private OrderTotalsResponse totals;
}
