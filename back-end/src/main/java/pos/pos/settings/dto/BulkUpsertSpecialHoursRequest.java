package pos.pos.settings.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
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
public class BulkUpsertSpecialHoursRequest {

    @Valid
    @NotEmpty(message = "items is required")
    private List<UpsertSpecialHourRequest> items;
}
