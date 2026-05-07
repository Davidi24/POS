package pos.pos.tables.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReorderTableCategoriesRequest {

    @NotNull(message = "categoryIds is required")
    private List<@NotNull(message = "categoryIds must not contain null values") UUID> categoryIds;
}
