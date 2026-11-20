package pos.pos.role.dto;

import lombok.*;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {

    private UUID id;
    private String code;
    private String name;
    private String description;
    private Long rank;
    private Boolean isSystem;
    private Boolean isActive;
    private Boolean isAssignable;
    private Boolean isProtected;
    // Inside the restaurant being worked in: whether its people get paid, the Salary team, and its picture.
    private Boolean paid;
    private String payTeam;
    private String picture;

}
