package pos.pos.menu.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pos.pos.menu.entity.Menu;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.policy.RestaurantPolicy;
import pos.pos.security.scope.ActorScope;

@Service
@RequiredArgsConstructor
public class MenuPolicy {

    private final RestaurantPolicy restaurantPolicy;

    public boolean canAccess(ActorScope scope, Restaurant restaurant) {
        return restaurantPolicy.canAccess(scope, restaurant);
    }

    public boolean canAccess(ActorScope scope, Menu menu) {
        return canAccess(scope, menu.getRestaurant());
    }

    public boolean canManage(ActorScope scope, Restaurant restaurant) {
        return restaurantPolicy.canManage(scope, restaurant);
    }

    public boolean canManage(ActorScope scope, Menu menu) {
        return canManage(scope, menu.getRestaurant());
    }

    public boolean canCreate(ActorScope scope, Restaurant restaurant) {
        return canManage(scope, restaurant);
    }

    public void assertCanAccess(ActorScope scope, Restaurant restaurant) {
        restaurantPolicy.assertCanAccess(scope, restaurant);
    }

    public void assertCanAccess(ActorScope scope, Menu menu) {
        assertCanAccess(scope, menu.getRestaurant());
    }

    public void assertCanManage(ActorScope scope, Restaurant restaurant) {
        restaurantPolicy.assertCanManage(scope, restaurant);
    }

    public void assertCanManage(ActorScope scope, Menu menu) {
        assertCanManage(scope, menu.getRestaurant());
    }

    public void assertCanCreate(ActorScope scope, Restaurant restaurant) {
        assertCanManage(scope, restaurant);
    }
}
