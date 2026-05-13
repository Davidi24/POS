package pos.pos.device.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.device.enums.DeviceAssignmentType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceAssignmentResponse {

    private UUID id;
    private UUID deviceId;
    private UUID branchId;
    private String branchName;
    private UUID userId;
    private String userEmail;
    private String userDisplayName;
    private DeviceAssignmentType assignmentType;
    private OffsetDateTime assignedAt;
    private OffsetDateTime unassignedAt;
    private Boolean active;
    private UUID assignedBy;
    private String assignedByDisplayName;
    private String notes;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
