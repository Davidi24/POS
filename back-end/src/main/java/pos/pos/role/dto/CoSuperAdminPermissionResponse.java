package pos.pos.role.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// One permission a Co-Super-Admin can hold, and whether the Super Admin has it ticked for this person.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoSuperAdminPermissionResponse {

    private String code;
    private String name;
    private String description;
    private boolean granted;
}
