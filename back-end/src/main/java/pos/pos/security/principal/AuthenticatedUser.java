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
    private final String email;
    private final String firstName;
    private final String lastName;
    private final String phone;
    private final boolean active;

    public static AuthenticatedUser from(User user) {
        return AuthenticatedUser.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .active(user.isActive())
                .build();
    }

    @Override
    public String getName() {
        return email;
    }
}
