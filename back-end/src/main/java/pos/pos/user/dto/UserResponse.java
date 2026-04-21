package pos.pos.user.dto;

import lombok.*;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    private UUID id;
    private String email;
    private String username;
    private String firstName;
    private String lastName;
    private String phone;
    private Boolean isActive;
    private Boolean emailVerified;
    private Boolean phoneVerified;
    private List<String> roles;

}
