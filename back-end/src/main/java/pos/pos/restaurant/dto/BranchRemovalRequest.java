package pos.pos.restaurant.dto;

import jakarta.validation.constraints.*;

public record BranchRemovalRequest(
        @NotNull StaffAction staffAction,
        @NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max=200) String confirmedName,
        @NotBlank @Size(max=64) String previewToken
) {
    public enum StaffAction { KEEP, DEACTIVATE }
}
