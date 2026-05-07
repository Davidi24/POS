package pos.pos.settings.dto;

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
public class CopyBusinessHoursResponse {

    private UUID sourceBranchId;
    private List<UUID> targetBranchIds;
    private Integer copiedDaysPerBranch;
}
