package pos.pos.tables.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTableLayoutRequest {

    @NotNull(message = "items is required")
    @Size(max = 2000, message = "items can have at most 2000 values")
    private List<@Valid TableLayoutItemRequest> items;
}
