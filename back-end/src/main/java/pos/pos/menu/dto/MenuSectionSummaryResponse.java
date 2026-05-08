package pos.pos.menu.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MenuSectionSummaryResponse {

    private UUID id;
    private String name;
    private String description;
    private Boolean active;
    private Integer displayOrder;
    private List<MenuItemSummaryResponse> items;
}
