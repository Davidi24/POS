package pos.pos.settings.dto;

import jakarta.validation.constraints.NotEmpty;
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
public class BulkDeleteSpecialHoursRequest {

    @NotEmpty(message = "specialHourIds is required")
    private List<UUID> specialHourIds;
}
