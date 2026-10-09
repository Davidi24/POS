package pos.pos.menu.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// One online section, for choosing where a dish goes online.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlineMenuSectionResponse {

    private UUID id;
    private String name;
    private Integer displayOrder;
    private Long itemCount;
}
