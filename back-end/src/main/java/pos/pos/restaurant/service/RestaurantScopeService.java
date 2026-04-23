package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import pos.pos.exception.restaurant.BranchNotFoundException;
import pos.pos.exception.restaurant.RestaurantAccessNotAllowedException;
import pos.pos.exception.restaurant.RestaurantCreationNotAllowedException;
import pos.pos.exception.restaurant.RestaurantDeletionNotAllowedException;
import pos.pos.exception.restaurant.RestaurantManagementNotAllowedException;
import pos.pos.exception.restaurant.RestaurantNotFoundException;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.user.entity.User;
import pos.pos.user.repository.UserRepository;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantScopeService {

    private final RestaurantRepository restaurantRepository;
    private final BranchRepository branchRepository;
    private final UserRepository userRepository;
    private final RoleHierarchyService roleHierarchyService;

    public Restaurant requireExistingRestaurant(UUID restaurantId) {
        return restaurantRepository.findByIdAndDeletedAtIsNull(restaurantId)
                .orElseThrow(RestaurantNotFoundException::new);
    }

    public Restaurant requireAccessibleRestaurant(Authentication authentication, UUID restaurantId) {
        Restaurant restaurant = requireExistingRestaurant(restaurantId);
        assertCanAccessRestaurant(authentication, restaurant);
        return restaurant;
    }

    public Restaurant requireManageableRestaurant(Authentication authentication, UUID restaurantId) {
        Restaurant restaurant = requireExistingRestaurant(restaurantId);
        assertCanManageRestaurant(authentication, restaurant);
        return restaurant;
    }

    public Branch requireAccessibleBranch(Authentication authentication, UUID restaurantId, UUID branchId) {
        Branch branch = requireExistingBranch(restaurantId, branchId);
        assertCanAccessRestaurant(authentication, branch.getRestaurant());
        return branch;
    }

    public Branch requireManageableBranch(Authentication authentication, UUID restaurantId, UUID branchId) {
        Branch branch = requireExistingBranch(restaurantId, branchId);
        assertCanManageRestaurant(authentication, branch.getRestaurant());
        return branch;
    }

    public void assertCanCreateRestaurant(Authentication authentication) {
        if (!roleHierarchyService.isSuperAdmin(authentication)) {
            throw new RestaurantCreationNotAllowedException();
        }
    }

    public void assertCanDeleteRestaurant(Authentication authentication) {
        if (!roleHierarchyService.isSuperAdmin(authentication)) {
            throw new RestaurantDeletionNotAllowedException();
        }
    }

    public UUID currentUserId(Authentication authentication) {
        return roleHierarchyService.currentUserId(authentication);
    }

    public User currentActor(Authentication authentication) {
        return userRepository.findByIdAndDeletedAtIsNull(currentUserId(authentication))
                .orElseThrow(RestaurantManagementNotAllowedException::new);
    }

    private Branch requireExistingBranch(UUID restaurantId, UUID branchId) {
        return branchRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(branchId, restaurantId)
                .orElseThrow(BranchNotFoundException::new);
    }

    private void assertCanAccessRestaurant(Authentication authentication, Restaurant restaurant) {
        if (roleHierarchyService.isSuperAdmin(authentication)) {
            return;
        }

        User actor = currentActor(authentication);
        if (!Objects.equals(actor.getRestaurantId(), restaurant.getId())
                && !Objects.equals(actor.getId(), restaurant.getOwnerId())) {
            throw new RestaurantAccessNotAllowedException();
        }
    }

    private void assertCanManageRestaurant(Authentication authentication, Restaurant restaurant) {
        if (roleHierarchyService.isSuperAdmin(authentication)) {
            return;
        }

        User actor = currentActor(authentication);
        if (!Objects.equals(actor.getRestaurantId(), restaurant.getId())
                && !Objects.equals(actor.getId(), restaurant.getOwnerId())) {
            throw new RestaurantManagementNotAllowedException();
        }
    }
}
