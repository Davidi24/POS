package pos.pos.role.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRoleRequest {

    private String name;
    private String description;

}