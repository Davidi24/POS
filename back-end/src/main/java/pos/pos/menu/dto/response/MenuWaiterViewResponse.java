package pos.pos.menu.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

// What waiters see: special menus only, or these menus.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuWaiterViewResponse {
    private Boolean specialMenusOnly;
    private List<UUID> shownMenuIds;
}
