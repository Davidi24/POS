package pos.pos.menu.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
/**
 * Management menu view. MenuController exposes this shape only to callers
 * with menu-management permissions.
 */
public class MenuResponse implements MenuViewResponse {

    private UUID id; //same logic of UUID, it's menu's ID
    private MenuRestaurantSummaryResponse restaurant;
    private String code;
    private String name;
    private String description;
    private Boolean active;
    private Integer displayOrder;
    private LocalTime availableFrom;
    private LocalTime availableUntil;
    private LocalDate availableFromDate;
    private LocalDate availableUntilDate;
    private String color;
    private Integer itemCount;
    private UUID createdBy; //using UUID to store ID of the user who created the menu, not the full User
    private UUID updatedBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<MenuSectionSummaryResponse> sections;
}
