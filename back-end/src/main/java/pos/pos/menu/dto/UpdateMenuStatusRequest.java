package pos.pos.menu.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMenuStatusRequest {

    @NotNull(message = "active is required")
    private Boolean active;
}
