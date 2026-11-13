package pos.pos.menu.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

// Items from other menus to copy into a section (e.g. a special menu reusing the regular dishes).
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportMenuItemsRequest {
    @NotEmpty(message = "itemIds is required")
    @Size(max = 100, message = "at most 100 items at once")
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") UUID> itemIds;
}
