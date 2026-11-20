package pos.pos.preorder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreOrderItemOptionResponse {

    private UUID optionItemId;
    private String name;
    private BigDecimal priceDelta;
    private Integer quantity;
    private String notes;
}
