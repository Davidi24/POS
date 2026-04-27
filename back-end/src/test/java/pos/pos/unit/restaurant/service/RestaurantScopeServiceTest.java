package pos.pos.unit.restaurant.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.policy.BranchPolicy;
import pos.pos.restaurant.policy.RestaurantPolicy;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.scope.ActorScope;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.user.entity.User;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@DisplayName("RestaurantScopeService")
class RestaurantScopeServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID OTHER_RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000020");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000030");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000040");

    private final RestaurantRepository restaurantRepository = mock(RestaurantRepository.class);
    private final BranchRepository branchRepository = mock(BranchRepository.class);
    private final ActorScopeService actorScopeService = mock(ActorScopeService.class);
    private final RestaurantPolicy restaurantPolicy = new RestaurantPolicy();
    private final BranchPolicy branchPolicy = new BranchPolicy(restaurantPolicy);
    private final RestaurantScopeService restaurantScopeService = new RestaurantScopeService(
            restaurantRepository,
            branchRepository,
            actorScopeService,
            restaurantPolicy,
            branchPolicy
    );

    @Test
    @DisplayName("requireAccessibleRestaurant should allow actors scoped to the same restaurant")
    void shouldAllowAccessibleRestaurantForSameTenant() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant(ACTOR_RESTAURANT_ID, OWNER_ID);

        given(actorScopeService.resolve(authentication)).willReturn(actorScope(false, ACTOR_RESTAURANT_ID));
        given(restaurantRepository.findByIdAndDeletedAtIsNull(ACTOR_RESTAURANT_ID)).willReturn(Optional.of(restaurant));

        Restaurant result = restaurantScopeService.requireAccessibleRestaurant(authentication, ACTOR_RESTAURANT_ID);

        assertThat(result).isEqualTo(restaurant);
    }

    @Test
    @DisplayName("requireAccessibleBranch should allow branch access through restaurant policy")
    void shouldAllowAccessibleBranchForSameTenant() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant(ACTOR_RESTAURANT_ID, OWNER_ID);
        Branch branch = branch(restaurant);

        given(actorScopeService.resolve(authentication)).willReturn(actorScope(false, ACTOR_RESTAURANT_ID));
        given(branchRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(BRANCH_ID, ACTOR_RESTAURANT_ID))
                .willReturn(Optional.of(branch));

        Branch result = restaurantScopeService.requireAccessibleBranch(authentication, ACTOR_RESTAURANT_ID, BRANCH_ID);

        assertThat(result).isEqualTo(branch);
    }

    @Test
    @DisplayName("requireAccessibleRestaurant should reject actors outside the tenant")
    void shouldRejectAccessibleRestaurantForForeignTenant() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant(OTHER_RESTAURANT_ID, OWNER_ID);

        given(actorScopeService.resolve(authentication)).willReturn(actorScope(false, ACTOR_RESTAURANT_ID));
        given(restaurantRepository.findByIdAndDeletedAtIsNull(OTHER_RESTAURANT_ID)).willReturn(Optional.of(restaurant));

        assertThatThrownBy(() -> restaurantScopeService.requireAccessibleRestaurant(authentication, OTHER_RESTAURANT_ID))
                .isInstanceOf(AuthException.class)
                .hasMessage("You are not allowed to access this restaurant");
    }

    @Test
    @DisplayName("assertCanCreateRestaurant should allow super admin scope")
    void shouldAllowCreateRestaurantForSuperAdmin() {
        Authentication authentication = authentication();

        given(actorScopeService.resolve(authentication)).willReturn(actorScope(true, null));

        assertThatCode(() -> restaurantScopeService.assertCanCreateRestaurant(authentication))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertCanDeleteRestaurant should reject non-super-admin scope")
    void shouldRejectDeleteRestaurantForNonSuperAdmin() {
        Authentication authentication = authentication();

        given(actorScopeService.resolve(authentication)).willReturn(actorScope(false, ACTOR_RESTAURANT_ID));

        assertThatThrownBy(() -> restaurantScopeService.assertCanDeleteRestaurant(authentication))
                .isInstanceOf(AuthException.class)
                .hasMessage("You are not allowed to delete restaurants");
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(ACTOR_ID)
                        .email("owner@pos.local")
                        .username("owner")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    private ActorScope actorScope(boolean superAdmin, UUID restaurantId) {
        return new ActorScope(
                actor(restaurantId),
                superAdmin,
                Set.of(superAdmin ? "SUPER_ADMIN" : "OWNER"),
                Set.of("RESTAURANTS_READ", "RESTAURANTS_UPDATE")
        );
    }

    private User actor(UUID restaurantId) {
        return User.builder()
                .id(ACTOR_ID)
                .email("owner@pos.local")
                .username("owner")
                .passwordHash("stored")
                .firstName("Olivia")
                .lastName("Owner")
                .status("ACTIVE")
                .isActive(true)
                .restaurantId(restaurantId)
                .build();
    }

    private Restaurant restaurant(UUID restaurantId, UUID ownerId) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(restaurantId);
        restaurant.setName("POS Main");
        restaurant.setOwnerId(ownerId);
        return restaurant;
    }

    private Branch branch(Restaurant restaurant) {
        Branch branch = new Branch();
        branch.setId(BRANCH_ID);
        branch.setRestaurant(restaurant);
        branch.setName("Downtown");
        branch.setCode("DOWN_TOWN");
        return branch;
    }
}
