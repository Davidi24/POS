package pos.pos.security.principal;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.springframework.security.core.AuthenticatedPrincipal;
import pos.pos.user.entity.User;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

@Getter
@Builder
@EqualsAndHashCode
public final class AuthenticatedUser implements AuthenticatedPrincipal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final UUID restaurantId;
    private final UUID defaultBranchId;
    private final String email;
    private final String username;
    private final String firstName;
    private final String lastName;
    private final String phone;
    private final boolean active;
    private final boolean emailVerified;
    private final boolean phoneVerified;

    public static AuthenticatedUser from(User user) {
        return AuthenticatedUser.builder()
                .id(user.getId())
                .restaurantId(user.getRestaurantId())
                .defaultBranchId(user.getDefaultBranchId())
                .email(user.getEmail())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .active(user.isActive())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .build();
    }

    @Override
    public String getName() {
        return username != null ? username : email;
    }
}
