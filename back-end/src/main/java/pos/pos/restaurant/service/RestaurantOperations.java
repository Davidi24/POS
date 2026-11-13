package pos.pos.restaurant.service;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.enums.BranchStatus;
import pos.pos.restaurant.enums.RestaurantStatus;

/** Check only when starting new operational work; administration and historical access stay available. */
public final class RestaurantOperations {
    private RestaurantOperations() {}
    public static void requireOpen(Branch branch) {
        var restaurant = branch.getRestaurant();
        if (!branch.isActive() || branch.getDeletedAt() != null || branch.getStatus() != BranchStatus.ACTIVE
                || !restaurant.isActive() || restaurant.getDeletedAt() != null || restaurant.getStatus() != RestaurantStatus.ACTIVE) {
            throw new AuthException("This branch is closed. Reopen it before starting new work.", HttpStatus.CONFLICT);
        }
    }
}
