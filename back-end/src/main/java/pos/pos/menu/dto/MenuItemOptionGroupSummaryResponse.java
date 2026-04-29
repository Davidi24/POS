package pos.pos.menu.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuItemOptionGroupSummaryResponse {

    private UUID linkId;
    private UUID optionGroupId;
    private String name;
    private String description;
    private Boolean active;
    private Integer displayOrder;
    private Integer minSelect;
    private Integer maxSelect;
    private Boolean required;
    private Integer minSelectOverride;
    private Integer maxSelectOverride;
    private Boolean requiredOverride;
}
