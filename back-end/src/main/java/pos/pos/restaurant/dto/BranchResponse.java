package pos.pos.restaurant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.restaurant.enums.BranchStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchResponse {

    private UUID id;
    private String name;
    private String code;
    private String description;
    private String email;
    private String phone;
    private Boolean isActive;
    private BranchStatus status;
    private UUID managerUserId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
