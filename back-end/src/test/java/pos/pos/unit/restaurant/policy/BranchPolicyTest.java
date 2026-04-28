package pos.pos.unit.restaurant.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.policy.BranchPolicy;
import pos.pos.restaurant.policy.RestaurantPolicy;
import pos.pos.security.scope.ActorScope;
import pos.pos.user.entity.User;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BranchPolicy")
class BranchPolicyTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

    private final BranchPolicy branchPolicy = new BranchPolicy(new RestaurantPolicy());

    @Test
    @DisplayName("assertCanAccess should allow actors in the same restaurant")
    void shouldAllowSameRestaurantAccess() {
        assertThatCode(() -> branchPolicy.assertCanAccess(actorScope(RESTAURANT_ID), branch()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertCanManage should allow actors in the same restaurant")
    void shouldAllowSameRestaurantManagement() {
        assertThatCode(() -> branchPolicy.assertCanManage(actorScope(RESTAURANT_ID), branch()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertCanAccess should reject actors outside the restaurant")
    void shouldRejectForeignRestaurantAccess() {
        assertThatThrownBy(() -> branchPolicy.assertCanAccess(actorScope(UUID.randomUUID()), branch()))
                .isInstanceOf(AuthException.class)
                .hasMessage("You are not allowed to access this restaurant");
    }

    @Test
    @DisplayName("assertCanManage should reject actors outside the restaurant")
    void shouldRejectForeignRestaurantManagement() {
        assertThatThrownBy(() -> branchPolicy.assertCanManage(actorScope(UUID.randomUUID()), branch()))
                .isInstanceOf(AuthException.class)
                .hasMessage("You are not allowed to manage this restaurant");
    }

    private ActorScope actorScope(UUID restaurantId) {
        return new ActorScope(
                User.builder()
                        .id(ACTOR_ID)
                        .email("owner@pos.local")
                        .username("owner")
                        .passwordHash("stored")
                        .firstName("Olivia")
                        .lastName("Owner")
                        .status("ACTIVE")
                        .isActive(true)
                        .restaurantId(restaurantId)
                        .build(),
                false,
                Set.of("OWNER"),
                Set.of("RESTAURANTS_READ", "RESTAURANTS_UPDATE")
        );
    }

    private Branch branch() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setName("POS Main");

        Branch branch = new Branch();
        branch.setId(BRANCH_ID);
        branch.setRestaurant(restaurant);
        branch.setName("Downtown");
        branch.setCode("DOWN_TOWN");
        return branch;
    }
}
