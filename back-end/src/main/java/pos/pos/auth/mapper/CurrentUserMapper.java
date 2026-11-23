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
    private final pos.pos.restaurant.repository.BranchRepository branches;

    private pos.pos.restaurant.repository.RestaurantRepository restaurants;

    public CurrentUserMapper(pos.pos.restaurant.repository.BranchRepository branches) { this.branches = branches; }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setRestaurants(pos.pos.restaurant.repository.RestaurantRepository restaurants) { this.restaurants = restaurants; }

    private String restaurantName(java.util.UUID restaurantId) {
        if (restaurantId == null || restaurants == null) return null;
        return restaurants.findByIdAndDeletedAtIsNull(restaurantId).map(pos.pos.restaurant.entity.Restaurant::getName).orElse(null);
    }

    private String restaurantLogo(java.util.UUID restaurantId) {
        if (restaurantId == null || restaurants == null) return null;
        return restaurants.findLogoUrl(restaurantId).orElse(null);
    }

    private String branchName(java.util.UUID restaurantId, java.util.UUID branchId) {
        if (restaurantId == null || branchId == null) return null;
        return branches.findByIdAndRestaurantIdAndDeletedAtIsNull(branchId, restaurantId).map(pos.pos.restaurant.entity.Branch::getName).orElse(null);
    }

    /** Login, refresh and session restoration never revive a closed or removed default branch. */
    private java.util.UUID availableBranch(java.util.UUID restaurantId, java.util.UUID branchId) {
        if (restaurantId == null || branchId == null) return null;
        return branches.findByIdAndRestaurantIdAndDeletedAtIsNull(branchId, restaurantId)
                .filter(b -> b.isActive() && b.getStatus() == pos.pos.restaurant.enums.BranchStatus.ACTIVE)
                .map(pos.pos.restaurant.entity.Branch::getId).orElse(null);
    }

    public CurrentUserResponse toCurrentUserResponse(User user, List<String> roles, List<String> permissions) {
        if (user == null) {
            return null;
        }

        return CurrentUserResponse.builder()
                .id(user.getId())
                .restaurantId(user.getRestaurantId())
                .defaultBranchId(availableBranch(user.getRestaurantId(), user.getDefaultBranchId()))
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
                .restaurantName(restaurantName(user.getRestaurantId()))
                .restaurantLogoUrl(restaurantLogo(user.getRestaurantId()))
                .photoUrl(user.getAvatarUrl())
                .language(user.getLanguage())
                .branchName(branchName(user.getRestaurantId(), availableBranch(user.getRestaurantId(), user.getDefaultBranchId())))
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
                .restaurantId(user.getRestaurantId())
                .defaultBranchId(availableBranch(user.getRestaurantId(), user.getDefaultBranchId()))
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
                .restaurantName(restaurantName(user.getRestaurantId()))
                .restaurantLogoUrl(restaurantLogo(user.getRestaurantId()))
                .photoUrl(user.getPhotoUrl())
                .language(user.getLanguage())
                .branchName(branchName(user.getRestaurantId(), availableBranch(user.getRestaurantId(), user.getDefaultBranchId())))
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
