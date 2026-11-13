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

// Menu → "Menus shown" → Change position: the menus in their new order (first = shown first).
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MenuOrderRequest {

    @NotNull(message = "restaurantId is required")
    private UUID restaurantId;

    @NotNull(message = "menuIds is required")
    @Size(min = 1, max = 500, message = "menuIds must have between 1 and 500 menus")
    private List<@NotNull(message = "must not contain empty values") UUID> menuIds;
}
