package pos.pos.menu.dto;

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
public class OptionItemResponse {

    private UUID id;
    private UUID optionGroupId;
    private String code;
    private String name;
    private BigDecimal priceDelta;
    private Boolean available;
    private Integer displayOrder;
}
