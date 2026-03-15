package pos.pos.auth.mapper;

import org.springframework.stereotype.Component;
import pos.pos.auth.dto.RegisterRequest;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.entity.User;

import java.time.OffsetDateTime;

@Component
public class AuthMapper {

    public User toUser(RegisterRequest request, String passwordHash) {

        OffsetDateTime now = OffsetDateTime.now();

        return User.builder()
                .email(request.getEmail())
                .passwordHash(passwordHash)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    public UserResponse toUserResponse(User user) {
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