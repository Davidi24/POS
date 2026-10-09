package pos.pos.menu.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PublicMenuResponse {

    private UUID id;
    private String code;
    private String name;
    private String description;
    private Integer displayOrder;
    private LocalDate availableFromDate;
    private LocalDate availableUntilDate;
    private List<PublicMenuSectionResponse> sections;
}
