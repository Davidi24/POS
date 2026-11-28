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
public class CurrentUserResponse {

    private UUID id;
    private UUID restaurantId;
    private UUID defaultBranchId;
    private String email;
    private String username;
    private String firstName;
    private String lastName;
    private String phone;
    private Boolean isActive;
    private boolean emailVerified;
    private boolean phoneVerified;
    private List<String> roles;
    private List<String> permissions;
    // For the person's own profile and menu (user, 2026-11-18): their restaurant's and default branch's names.
    private String restaurantName;
    private String branchName;
    // The person's own photo (Profile), or null for their initials.
    private String photoUrl;
    // The language the person picked in Profile (en, it, es, sq), or null for English.
    private String language;
    // Their restaurant's logo, shown in the app's bars instead of the app's own (user, 2026-11-23), or null.
    private String restaurantLogoUrl;
}
