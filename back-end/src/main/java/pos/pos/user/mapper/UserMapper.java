package pos.pos.user.mapper;

import org.springframework.stereotype.Component;
import pos.pos.restaurant.dto.CreateRestaurantOwnerRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.user.dto.UserResponse;
import pos.pos.user.entity.User;
import pos.pos.user.service.UserIdentityService;

import java.util.List;
import java.util.UUID;

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
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .isActive(user.isActive())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .roles(roles == null ? List.of() : List.copyOf(roles))
                .build();
    }

    public User toRestaurantOwner(
            CreateRestaurantOwnerRequest request,
            UserIdentityService.NormalizedUserIdentity identity,
            String passwordHash,
            UUID restaurantId,
            UUID actorId
    ) {
        return User.builder()
                .email(identity.email())
                .username(identity.username())
                .passwordHash(passwordHash)
                .restaurantId(restaurantId)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(false)
                .phoneVerified(false)
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();
    }

    public User toRestaurantOwner(
            Restaurant restaurant,
            UserIdentityService.NormalizedUserIdentity identity,
            String passwordHash,
            UUID actorId
    ) {
        return User.builder()
                .email(identity.email())
                .username(identity.username())
                .passwordHash(passwordHash)
                .restaurantId(restaurant.getId())
                .firstName(restaurant.getPendingOwnerFirstName())
                .lastName(restaurant.getPendingOwnerLastName())
                .phone(restaurant.getPendingOwnerPhone())
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(false)
                .phoneVerified(false)
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();
    }
}
