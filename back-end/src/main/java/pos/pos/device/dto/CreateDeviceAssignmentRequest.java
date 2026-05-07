package pos.pos.device.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.device.enums.DeviceAssignmentType;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateDeviceAssignmentRequest {

    @NotNull(message = "assignmentType is required")
    private DeviceAssignmentType assignmentType;

    private UUID branchId;

    private UUID userId;

    @Size(max = 1000, message = "notes must be at most 1000 characters")
    private String notes;
}
