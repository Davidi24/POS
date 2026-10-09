package pos.pos.tables.dto;

import jakarta.validation.constraints.Size;
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
public class UpdateTableCategoryTablesRequest {

    @NotNull(message = "tableIds is required")
    @Size(max = 2000, message = "tableIds can have at most 2000 values")
    private List<@NotNull(message = "tableIds must not contain null values") UUID> tableIds;
}
