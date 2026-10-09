package pos.pos.tables.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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

    @Positive(message = "guestCount must be greater than zero")
    @Max(value = 1000, message = "guestCount must be at most 1000")
    private Integer guestCount;
}
