package pos.pos.preorder.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreOrderItemResponse {

    private UUID id;
    private UUID menuItemId;
    private UUID variantId;
    private String itemName;
    private String variantName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal variantPriceDelta;
    private BigDecimal priceDeltaTotal;
    private BigDecimal lineTotal;
    private String notes;
    private List<PreOrderItemOptionResponse> options;
}
