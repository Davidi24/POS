package pos.pos.menu.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MenuItemSummaryResponse {

    private UUID id;
    private String sku;
    private String name;
    private String description;
    private BigDecimal basePrice;
    private String imageUrl;
    private Boolean available;
    private Boolean sendToKitchen;
    private Boolean showOnline;
    private UUID onlineSectionId;
    private String onlineSectionName;
    private Integer displayOrder;
    private List<String> ingredients;
    private Integer orderBeforeHours;
    private List<String> occasionCodes;
    private List<MenuVariantSummaryResponse> variants;
    private List<MenuItemOptionGroupSummaryResponse> optionGroups;
}
