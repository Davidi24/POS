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
public class OptionGroupResponse {

    private UUID id;
    private UUID restaurantId;
    private OptionGroupTypeResponse type;
    private String name;
    private String description;
    private Integer minSelect;
    private Integer maxSelect;
    private Boolean required;
    private Boolean active;
    private Integer displayOrder;
    private List<OptionItemResponse> items;
}
