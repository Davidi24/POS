package pos.pos.unit.menu.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.exception.auth.AuthException;
import pos.pos.menu.entity.Menu;
import pos.pos.menu.policy.MenuPolicy;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.policy.RestaurantPolicy;
import pos.pos.security.scope.ActorScope;
import pos.pos.user.entity.User;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MenuPolicy")
class MenuPolicyTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID MENU_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");

    private final MenuPolicy menuPolicy = new MenuPolicy(new RestaurantPolicy());

    @Test
    @DisplayName("assertCanAccess should allow actors in the same restaurant")
    void shouldAllowSameRestaurantAccess() {
        assertThatCode(() -> menuPolicy.assertCanAccess(actorScope(RESTAURANT_ID), menu()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertCanManage should allow actors in the same restaurant")
    void shouldAllowSameRestaurantManagement() {
        assertThatCode(() -> menuPolicy.assertCanManage(actorScope(RESTAURANT_ID), menu()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertCanCreate should allow actors in the same restaurant")
    void shouldAllowCreateWithinRestaurantScope() {
        assertThatCode(() -> menuPolicy.assertCanCreate(actorScope(RESTAURANT_ID), restaurant()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertCanAccess should reject actors outside the restaurant")
    void shouldRejectForeignRestaurantAccess() {
        assertThatThrownBy(() -> menuPolicy.assertCanAccess(actorScope(UUID.randomUUID()), menu()))
                .isInstanceOf(AuthException.class)
                .hasMessage("You are not allowed to access this restaurant");
    }

    @Test
    @DisplayName("assertCanManage should reject actors outside the restaurant")
    void shouldRejectForeignRestaurantManagement() {
        assertThatThrownBy(() -> menuPolicy.assertCanManage(actorScope(UUID.randomUUID()), menu()))
                .isInstanceOf(AuthException.class)
                .hasMessage("You are not allowed to manage this restaurant");
    }

    @Test
    @DisplayName("assertCanCreate should reject actors outside the restaurant")
    void shouldRejectCreateOutsideRestaurantScope() {
        assertThatThrownBy(() -> menuPolicy.assertCanCreate(actorScope(UUID.randomUUID()), restaurant()))
                .isInstanceOf(AuthException.class)
                .hasMessage("You are not allowed to manage this restaurant");
    }

    private ActorScope actorScope(UUID restaurantId) {
        return new ActorScope(
                ACTOR_ID,
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
                Set.of("MENUS_READ", "MENUS_UPDATE")
        );
    }

    private Menu menu() {
        Menu menu = new Menu();
        menu.setId(MENU_ID);
        menu.setRestaurant(restaurant());
        menu.setCode("BREAKFAST");
        menu.setName("Breakfast");
        return menu;
    }

    private Restaurant restaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setName("POS Main");
        return restaurant;
    }
}
