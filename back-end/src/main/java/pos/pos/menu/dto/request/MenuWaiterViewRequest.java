package pos.pos.menu.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

// Menu → "Menus shown": special menus only, or the menus waiters see.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuWaiterViewRequest {

    @NotNull(message = "restaurantId is required")
    private UUID restaurantId;

    @NotNull(message = "specialMenusOnly is required")
    private Boolean specialMenusOnly;

    @NotNull(message = "shownMenuIds is required")
    @Size(max = 500, message = "shownMenuIds must have at most 500 menus")
    private List<@NotNull(message = "must not contain empty values") UUID> shownMenuIds;
}
