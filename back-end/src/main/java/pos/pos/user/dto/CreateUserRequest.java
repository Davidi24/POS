package pos.pos.user.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private String phone;

}