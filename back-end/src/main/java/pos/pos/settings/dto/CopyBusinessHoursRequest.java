package pos.pos.settings.dto;

import jakarta.validation.constraints.Size;
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
public class CopyBusinessHoursRequest {

    @NotEmpty(message = "targetBranchIds is required")
    @Size(max = 500, message = "targetBranchIds can have at most 500 values")
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") UUID> targetBranchIds;
}
