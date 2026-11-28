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
public class UpdateFloorsRequest {

    @NotNull(message = "renames is required")
    @Size(max = 200, message = "renames can have at most 200 values")
    private List<@Valid FloorRenameRequest> renames;
}
