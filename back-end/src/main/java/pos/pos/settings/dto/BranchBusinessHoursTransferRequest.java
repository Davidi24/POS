package pos.pos.settings.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
public class BranchBusinessHoursTransferRequest {

    @NotBlank(message = "branchCode is required")
    private String branchCode;

    @Valid
    @NotEmpty(message = "items is required")
    @Size(min = 7, max = 7, message = "items must contain exactly 7 day definitions")
    private List<UpsertBusinessHourRequest> items;
}
