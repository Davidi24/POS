package pos.pos.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeResponse {

    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private String phone;
    private boolean isActive;
    private List<String> roles;
    private List<String> permissions;
}