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
 * staff. Audit metadata is intentionally omitted; the restaurant's id/name and
 * the display fields (colour, hours, special menu and its event) are included,
 * because the till checks the restaurant and waiters' menus follow Menu → "Menus shown".
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
    private MenuRestaurantSummaryResponse restaurant;
    private String color;
    private java.time.LocalTime availableFrom;
    private java.time.LocalTime availableUntil;
    private java.time.LocalDate availableFromDate;
    private java.time.LocalDate availableUntilDate;
    private Boolean special;
    private MenuEventSummaryResponse event;
    private Boolean shownToWaiters;
    private Integer itemCount;
    private List<MenuSectionSummaryResponse> sections;
}
