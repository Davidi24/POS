package pos.pos.menu.dto.update;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

// Every online section of the restaurant, in the new order.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReorderOnlineMenuSectionsRequest {

    @NotEmpty(message = "sectionIds must not be empty")
    private List<UUID> sectionIds;
}
