package pos.pos.menu.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class MenuVariantSummaryResponse {

    private UUID id;
    private String name;
    private String sku;
    private BigDecimal priceDelta;
    @JsonProperty("default")
    private Boolean isDefault;
    private Boolean active;
    private Integer displayOrder;
}
