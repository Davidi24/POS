package pos.pos.tables.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.tables.enums.TableStatus;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateTableStatusRequest {

    @NotNull(message = "status is required")
    private TableStatus status;
}
