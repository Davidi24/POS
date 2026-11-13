package pos.pos.settings.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
public class BranchSpecialHoursTransferRequest {

    @NotBlank(message = "branchCode is required")
    @Size(max = 100, message = "branchCode must be at most 100 characters")
    private String branchCode;

    @Valid
    @NotNull(message = "items is required")
    private List<@jakarta.validation.constraints.NotNull(message = "must not contain empty values") UpsertSpecialHourRequest> items;
}
