package pos.pos.unit.restaurant.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.restaurant.RestaurantOwnershipChangeNotAllowedException;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.policy.RestaurantPolicy;
import pos.pos.security.scope.ActorScope;
import pos.pos.user.entity.User;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RestaurantPolicy")
class RestaurantPolicyTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final UUID NEW_OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000030");

    private final RestaurantPolicy restaurantPolicy = new RestaurantPolicy();

    @Test
    @DisplayName("canAccess should allow super admin actors")
    void shouldAllowSuperAdminAccess() {
        boolean result = restaurantPolicy.canAccess(actorScope(true, null, ACTOR_ID), restaurant(OWNER_ID));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("canAccess should allow actors assigned to the same restaurant")
    void shouldAllowSameRestaurantAccess() {
        boolean result = restaurantPolicy.canAccess(actorScope(false, RESTAURANT_ID, ACTOR_ID), restaurant(OWNER_ID));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("canAccess should allow the restaurant owner")
    void shouldAllowOwnerAccess() {
        boolean result = restaurantPolicy.canAccess(actorScope(false, null, OWNER_ID), restaurant(OWNER_ID));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("assertCanAccess should reject foreign actors")
    void shouldRejectForeignActorAccess() {
        assertThatThrownBy(() -> restaurantPolicy.assertCanAccess(
                actorScope(false, UUID.randomUUID(), ACTOR_ID),
                restaurant(OWNER_ID)
        )).isInstanceOf(AuthException.class)
                .hasMessage("You are not allowed to access this restaurant");
    }

    @Test
    @DisplayName("assertCanManage should reject foreign actors")
    void shouldRejectForeignActorManagement() {
        assertThatThrownBy(() -> restaurantPolicy.assertCanManage(
                actorScope(false, UUID.randomUUID(), ACTOR_ID),
                restaurant(OWNER_ID)
        )).isInstanceOf(AuthException.class)
                .hasMessage("You are not allowed to manage this restaurant");
    }

    @Test
    @DisplayName("assertCanCreate should allow only super admin actors")
    void shouldRestrictCreateToSuperAdmin() {
        assertThatCode(() -> restaurantPolicy.assertCanCreate(actorScope(true, null, ACTOR_ID)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> restaurantPolicy.assertCanCreate(actorScope(false, RESTAURANT_ID, ACTOR_ID)))
                .isInstanceOf(AuthException.class)
                .hasMessage("You are not allowed to create restaurants");
    }

    @Test
    @DisplayName("assertCanDelete should allow only super admin actors")
    void shouldRestrictDeleteToSuperAdmin() {
        assertThatCode(() -> restaurantPolicy.assertCanDelete(actorScope(true, null, ACTOR_ID)))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> restaurantPolicy.assertCanDelete(actorScope(false, RESTAURANT_ID, ACTOR_ID)))
                .isInstanceOf(AuthException.class)
                .hasMessage("You are not allowed to delete restaurants");
    }

    @Test
    @DisplayName("assertCanChangeOwner should allow non-super-admin actors when the owner is unchanged")
    void shouldAllowUnchangedOwnerForNonSuperAdmin() {
        assertThatCode(() -> restaurantPolicy.assertCanChangeOwner(
                actorScope(false, RESTAURANT_ID, ACTOR_ID),
                restaurant(OWNER_ID),
                OWNER_ID
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertCanChangeOwner should allow super admin actors to change owner")
    void shouldAllowOwnerChangeForSuperAdmin() {
        assertThatCode(() -> restaurantPolicy.assertCanChangeOwner(
                actorScope(true, null, ACTOR_ID),
                restaurant(OWNER_ID),
                NEW_OWNER_ID
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertCanChangeOwner should reject owner changes by non-super-admin actors")
    void shouldRejectOwnerChangeForNonSuperAdmin() {
        assertThatThrownBy(() -> restaurantPolicy.assertCanChangeOwner(
                actorScope(false, RESTAURANT_ID, ACTOR_ID),
                restaurant(OWNER_ID),
                NEW_OWNER_ID
        )).isInstanceOf(RestaurantOwnershipChangeNotAllowedException.class);
    }

    private ActorScope actorScope(boolean superAdmin, UUID restaurantId, UUID userId) {
        return new ActorScope(
                User.builder()
                        .id(userId)
                        .email("owner@pos.local")
                        .username("owner")
                        .passwordHash("stored")
                        .firstName("Olivia")
                        .lastName("Owner")
                        .status("ACTIVE")
                        .isActive(true)
                        .restaurantId(restaurantId)
                        .build(),
                superAdmin,
                Set.of(superAdmin ? "SUPER_ADMIN" : "OWNER"),
                Set.of("RESTAURANTS_READ", "RESTAURANTS_UPDATE")
        );
    }

    private Restaurant restaurant(UUID ownerId) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setName("POS Main");
        restaurant.setOwnerId(ownerId);
        return restaurant;
    }
}
