package pos.pos.restaurant.policy;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pos.pos.restaurant.entity.Branch;
import pos.pos.security.scope.ActorScope;

@Service
@RequiredArgsConstructor
public class BranchPolicy {

    private final RestaurantPolicy restaurantPolicy;

    public boolean canAccess(ActorScope scope, Branch branch) {
        return restaurantPolicy.canAccess(scope, branch.getRestaurant());
    }

    public boolean canManage(ActorScope scope, Branch branch) {
        return restaurantPolicy.canManage(scope, branch.getRestaurant());
    }

    public void assertCanAccess(ActorScope scope, Branch branch) {
        restaurantPolicy.assertCanAccess(scope, branch.getRestaurant());
    }

    public void assertCanManage(ActorScope scope, Branch branch) {
        restaurantPolicy.assertCanManage(scope, branch.getRestaurant());
    }
}
