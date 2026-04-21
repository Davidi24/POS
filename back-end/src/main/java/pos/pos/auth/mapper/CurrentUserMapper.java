package pos.pos.auth.mapper;

import org.springframework.stereotype.Component;
import pos.pos.auth.dto.CurrentUserResponse;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.user.entity.User;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@Component
public class CurrentUserMapper {

    public CurrentUserResponse toCurrentUserResponse(User user, List<String> roles, List<String> permissions) {
        if (user == null) {
            return null;
        }

        return CurrentUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .isActive(user.isActive())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .roles(normalizeCodes(roles))
                .permissions(normalizeCodes(permissions))
                .build();
    }

    public CurrentUserResponse toCurrentUserResponse(
            AuthenticatedUser user,
            List<String> roles,
            List<String> permissions
    ) {
        if (user == null) {
            return null;
        }

        return CurrentUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .isActive(user.isActive())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .roles(normalizeCodes(roles))
                .permissions(normalizeCodes(permissions))
                .build();
    }

    private List<String> normalizeCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }

        return List.copyOf(
                new LinkedHashSet<>(
                        codes.stream()
                                .filter(Objects::nonNull)
                                .map(String::trim)
                                .filter(value -> !value.isEmpty())
                                .toList()
                )
        );
    }
}
