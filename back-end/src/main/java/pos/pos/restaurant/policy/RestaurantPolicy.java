package pos.pos.restaurant.policy;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.restaurant.RestaurantOwnershipChangeNotAllowedException;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.security.scope.ActorScope;

import java.util.Objects;
import java.util.UUID;

@Service
public class RestaurantPolicy {

    public boolean canAccess(ActorScope scope, Restaurant restaurant) {
        return scope.superAdmin()
                || scope.belongsToRestaurant(restaurant.getId())
                || Objects.equals(scope.userId(), restaurant.getOwnerId());
    }

    public boolean canManage(ActorScope scope, Restaurant restaurant) {
        return canAccess(scope, restaurant);
    }

    public boolean canCreate(ActorScope scope) {
        return scope.superAdmin();
    }

    public boolean canDelete(ActorScope scope) {
        return scope.superAdmin();
    }

    public boolean canChangeOwner(ActorScope scope, Restaurant restaurant, UUID requestedOwnerUserId) {
        return scope.superAdmin() || Objects.equals(restaurant.getOwnerId(), requestedOwnerUserId);
    }

    public void assertCanAccess(ActorScope scope, Restaurant restaurant) {
        if (!canAccess(scope, restaurant)) {
            throw forbidden("You are not allowed to access this restaurant");
        }
    }

    public void assertCanManage(ActorScope scope, Restaurant restaurant) {
        if (!canManage(scope, restaurant)) {
            throw forbidden("You are not allowed to manage this restaurant");
        }
    }

    public void assertCanCreate(ActorScope scope) {
        if (!canCreate(scope)) {
            throw forbidden("You are not allowed to create restaurants");
        }
    }

    public void assertCanDelete(ActorScope scope) {
        if (!canDelete(scope)) {
            throw forbidden("You are not allowed to delete restaurants");
        }
    }

    public void assertCanChangeOwner(ActorScope scope, Restaurant restaurant, UUID requestedOwnerUserId) {
        if (!canChangeOwner(scope, restaurant, requestedOwnerUserId)) {
            throw new RestaurantOwnershipChangeNotAllowedException();
        }
    }

    private AuthException forbidden(String message) {
        return new AuthException(message, HttpStatus.FORBIDDEN);
    }
}
