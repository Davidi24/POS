package pos.pos.menu.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

/**
 * Operational menu view for read-only POS users such as waiters and kitchen
 * staff. Internal restaurant and audit metadata are intentionally omitted.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MenuCatalogResponse implements MenuViewResponse {

    private UUID id;
    private String code;
    private String name;
    private String description;
    private Boolean active;
    private Integer displayOrder;
    private List<MenuSectionSummaryResponse> sections;
}
