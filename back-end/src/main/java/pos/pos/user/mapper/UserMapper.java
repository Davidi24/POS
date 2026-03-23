package pos.pos.user.mapper;

import org.springframework.stereotype.Component;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.entity.User;

import java.util.List;

@Component
public class UserMapper {

    public UserResponse toUserResponse(User user) {
        return toUserResponse(user, List.of());
    }

    public UserResponse toUserResponse(User user, List<String> roles) {
        if (user == null) {
            return null;
        }

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .isActive(user.isActive())
                .roles(roles == null ? List.of() : List.copyOf(roles))
                .build();
    }
}
