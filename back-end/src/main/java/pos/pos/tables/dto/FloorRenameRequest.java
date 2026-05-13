package pos.pos.tables.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloorRenameRequest {

    @NotBlank(message = "from is required")
    @Size(max = 50, message = "from must be at most 50 characters")
    private String from;

    @NotBlank(message = "to is required")
    @Size(max = 50, message = "to must be at most 50 characters")
    private String to;
}
