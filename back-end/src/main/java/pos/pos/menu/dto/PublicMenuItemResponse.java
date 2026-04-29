package pos.pos.menu.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicMenuItemResponse {

    private UUID id;
    private String sku;
    private String name;
    private String description;
    private BigDecimal basePrice;
    private String imageUrl;
    private Integer displayOrder;
}
