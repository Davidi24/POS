package pos.pos.menu.dto.update;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

// Every dish in one online section, in the new order.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReorderOnlineMenuItemsRequest {

    @NotEmpty(message = "itemIds must not be empty")
    private List<UUID> itemIds;
}
