package pos.pos.user.mapper;

import org.springframework.stereotype.Component;
import pos.pos.user.entity.User;
import pos.pos.user.dto.UserResponse;

@Component
public class UserMapper {

    public UserResponse toUserResponse(User user) {
        if (user == null) return null;

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .isActive(user.getIsActive())
                .build();
    }

}