package pos.pos.unit.restaurant.bootstrap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.restaurant.bootstrap.LocalRestaurantSeedRunner;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.security.service.PasswordService;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("LocalRestaurantSeedRunner")
class LocalRestaurantSeedRunnerTest {

    private static final UUID OWNER_ROLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000999");

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordService passwordService;

    @InjectMocks
    private LocalRestaurantSeedRunner localRestaurantSeedRunner;

    @Test
    @DisplayName("run should create sample owners, roles, and restaurants when missing")
    void shouldCreateMissingSampleRestaurants() throws Exception {
        Role ownerRole = ownerRole();
        given(roleRepository.findByCode("OWNER")).willReturn(Optional.of(ownerRole));
        given(passwordService.hash(anyString())).willReturn("hashed-demo-password");
        given(userRepository.findById(any(UUID.class))).willReturn(Optional.empty());
        given(userRepository.findByEmailAndDeletedAtIsNull(anyString())).willReturn(Optional.empty());
        given(userRepository.findByUsernameAndDeletedAtIsNull(anyString())).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(userRoleRepository.existsByUserIdAndRoleId(any(UUID.class), any(UUID.class))).willReturn(false);
        given(userRoleRepository.save(any(UserRole.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(restaurantRepository.findById(any(UUID.class))).willReturn(Optional.empty());
        given(restaurantRepository.findBySlugAndDeletedAtIsNull(anyString())).willReturn(Optional.empty());
        given(restaurantRepository.save(any(Restaurant.class))).willAnswer(invocation -> invocation.getArgument(0));

        localRestaurantSeedRunner.run();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<Restaurant> restaurantCaptor = ArgumentCaptor.forClass(Restaurant.class);
        verify(userRepository, times(3)).save(userCaptor.capture());
        verify(userRoleRepository, times(3)).save(any(UserRole.class));
        verify(restaurantRepository, times(3)).save(restaurantCaptor.capture());

        List<User> savedOwners = userCaptor.getAllValues();
        assertThat(savedOwners)
                .extracting(User::getId)
                .containsExactly(
                        LocalRestaurantSeedRunner.DEMO_BISTRO_OWNER_ID,
                        LocalRestaurantSeedRunner.DEMO_PIZZA_OWNER_ID,
                        LocalRestaurantSeedRunner.DEMO_CAFE_OWNER_ID
                );
        assertThat(savedOwners)
                .extracting(User::getRestaurantId)
                .containsExactly(
                        LocalRestaurantSeedRunner.DEMO_BISTRO_ID,
                        LocalRestaurantSeedRunner.DEMO_PIZZA_ID,
                        LocalRestaurantSeedRunner.DEMO_CAFE_ID
                );

        List<Restaurant> savedRestaurants = restaurantCaptor.getAllValues();
        assertThat(savedRestaurants)
                .extracting(Restaurant::getId)
                .containsExactly(
                        LocalRestaurantSeedRunner.DEMO_BISTRO_ID,
                        LocalRestaurantSeedRunner.DEMO_PIZZA_ID,
                        LocalRestaurantSeedRunner.DEMO_CAFE_ID
                );
        assertThat(savedRestaurants)
                .extracting(Restaurant::getOwnerId)
                .containsExactly(
                        LocalRestaurantSeedRunner.DEMO_BISTRO_OWNER_ID,
                        LocalRestaurantSeedRunner.DEMO_PIZZA_OWNER_ID,
                        LocalRestaurantSeedRunner.DEMO_CAFE_OWNER_ID
                );
    }

    @Test
    @DisplayName("run should reuse existing valid sample owners and restaurants without saving")
    void shouldReuseExistingRestaurantsWithoutSaving() throws Exception {
        Role ownerRole = ownerRole();
        given(roleRepository.findByCode("OWNER")).willReturn(Optional.of(ownerRole));
        given(userRepository.findById(LocalRestaurantSeedRunner.DEMO_BISTRO_OWNER_ID))
                .willReturn(Optional.of(existingOwner(
                        LocalRestaurantSeedRunner.DEMO_BISTRO_OWNER_ID,
                        "bistro.owner@pos.local",
                        "demo.bistro.owner",
                        LocalRestaurantSeedRunner.DEMO_BISTRO_ID
                )));
        given(userRepository.findById(LocalRestaurantSeedRunner.DEMO_PIZZA_OWNER_ID))
                .willReturn(Optional.of(existingOwner(
                        LocalRestaurantSeedRunner.DEMO_PIZZA_OWNER_ID,
                        "pizza.owner@pos.local",
                        "demo.pizza.owner",
                        LocalRestaurantSeedRunner.DEMO_PIZZA_ID
                )));
        given(userRepository.findById(LocalRestaurantSeedRunner.DEMO_CAFE_OWNER_ID))
                .willReturn(Optional.of(existingOwner(
                        LocalRestaurantSeedRunner.DEMO_CAFE_OWNER_ID,
                        "cafe.owner@pos.local",
                        "demo.cafe.owner",
                        LocalRestaurantSeedRunner.DEMO_CAFE_ID
                )));
        given(userRoleRepository.existsByUserIdAndRoleId(any(UUID.class), any(UUID.class))).willReturn(true);
        given(restaurantRepository.findById(LocalRestaurantSeedRunner.DEMO_BISTRO_ID))
                .willReturn(Optional.of(existingRestaurant(
                        LocalRestaurantSeedRunner.DEMO_BISTRO_ID,
                        "Local Demo Bistro",
                        "Local Demo Bistro LLC",
                        "LOCAL_DEMO_BISTRO",
                        "local-demo-bistro",
                        LocalRestaurantSeedRunner.DEMO_BISTRO_OWNER_ID
                )));
        given(restaurantRepository.findById(LocalRestaurantSeedRunner.DEMO_PIZZA_ID))
                .willReturn(Optional.of(existingRestaurant(
                        LocalRestaurantSeedRunner.DEMO_PIZZA_ID,
                        "Local Demo Pizza",
                        "Local Demo Pizza LLC",
                        "LOCAL_DEMO_PIZZA",
                        "local-demo-pizza",
                        LocalRestaurantSeedRunner.DEMO_PIZZA_OWNER_ID
                )));
        given(restaurantRepository.findById(LocalRestaurantSeedRunner.DEMO_CAFE_ID))
                .willReturn(Optional.of(existingRestaurant(
                        LocalRestaurantSeedRunner.DEMO_CAFE_ID,
                        "Local Demo Cafe",
                        "Local Demo Cafe LLC",
                        "LOCAL_DEMO_CAFE",
                        "local-demo-cafe",
                        LocalRestaurantSeedRunner.DEMO_CAFE_OWNER_ID
                )));

        localRestaurantSeedRunner.run();

        verify(userRepository, never()).save(any(User.class));
        verify(restaurantRepository, never()).save(any(Restaurant.class));
        verify(userRoleRepository, never()).save(any(UserRole.class));
    }

    @Test
    @DisplayName("run should repair ownerless sample restaurants")
    void shouldRepairOwnerlessRestaurant() throws Exception {
        Role ownerRole = ownerRole();
        User bistroOwner = existingOwner(
                LocalRestaurantSeedRunner.DEMO_BISTRO_OWNER_ID,
                "bistro.owner@pos.local",
                "demo.bistro.owner",
                LocalRestaurantSeedRunner.DEMO_BISTRO_ID
        );
        Restaurant ownerlessRestaurant = existingRestaurant(
                LocalRestaurantSeedRunner.DEMO_BISTRO_ID,
                "Local Demo Bistro",
                "Local Demo Bistro LLC",
                "LOCAL_DEMO_BISTRO",
                "local-demo-bistro",
                null
        );
        ownerlessRestaurant.setDeletedAt(OffsetDateTime.now());
        ownerlessRestaurant.setActive(false);
        ownerlessRestaurant.setStatus(RestaurantStatus.ARCHIVED);

        given(roleRepository.findByCode("OWNER")).willReturn(Optional.of(ownerRole));
        given(userRepository.findById(LocalRestaurantSeedRunner.DEMO_BISTRO_OWNER_ID)).willReturn(Optional.of(bistroOwner));
        given(userRepository.findById(LocalRestaurantSeedRunner.DEMO_PIZZA_OWNER_ID))
                .willReturn(Optional.of(existingOwner(
                        LocalRestaurantSeedRunner.DEMO_PIZZA_OWNER_ID,
                        "pizza.owner@pos.local",
                        "demo.pizza.owner",
                        LocalRestaurantSeedRunner.DEMO_PIZZA_ID
                )));
        given(userRepository.findById(LocalRestaurantSeedRunner.DEMO_CAFE_OWNER_ID))
                .willReturn(Optional.of(existingOwner(
                        LocalRestaurantSeedRunner.DEMO_CAFE_OWNER_ID,
                        "cafe.owner@pos.local",
                        "demo.cafe.owner",
                        LocalRestaurantSeedRunner.DEMO_CAFE_ID
                )));
        given(userRoleRepository.existsByUserIdAndRoleId(any(UUID.class), any(UUID.class))).willReturn(true);
        given(restaurantRepository.findById(LocalRestaurantSeedRunner.DEMO_BISTRO_ID))
                .willReturn(Optional.of(ownerlessRestaurant));
        given(restaurantRepository.findById(LocalRestaurantSeedRunner.DEMO_PIZZA_ID))
                .willReturn(Optional.of(existingRestaurant(
                        LocalRestaurantSeedRunner.DEMO_PIZZA_ID,
                        "Local Demo Pizza",
                        "Local Demo Pizza LLC",
                        "LOCAL_DEMO_PIZZA",
                        "local-demo-pizza",
                        LocalRestaurantSeedRunner.DEMO_PIZZA_OWNER_ID
                )));
        given(restaurantRepository.findById(LocalRestaurantSeedRunner.DEMO_CAFE_ID))
                .willReturn(Optional.of(existingRestaurant(
                        LocalRestaurantSeedRunner.DEMO_CAFE_ID,
                        "Local Demo Cafe",
                        "Local Demo Cafe LLC",
                        "LOCAL_DEMO_CAFE",
                        "local-demo-cafe",
                        LocalRestaurantSeedRunner.DEMO_CAFE_OWNER_ID
                )));
        given(restaurantRepository.save(any(Restaurant.class))).willAnswer(invocation -> invocation.getArgument(0));

        localRestaurantSeedRunner.run();

        assertThat(ownerlessRestaurant.getDeletedAt()).isNull();
        assertThat(ownerlessRestaurant.isActive()).isTrue();
        assertThat(ownerlessRestaurant.getStatus()).isEqualTo(RestaurantStatus.ACTIVE);
        assertThat(ownerlessRestaurant.getOwnerId()).isEqualTo(LocalRestaurantSeedRunner.DEMO_BISTRO_OWNER_ID);
        verify(restaurantRepository, times(1)).save(ownerlessRestaurant);
    }

    private Role ownerRole() {
        Role role = new Role();
        role.setId(OWNER_ROLE_ID);
        role.setCode("OWNER");
        role.setName("Owner");
        role.setActive(true);
        return role;
    }

    private User existingOwner(UUID id, String email, String username, UUID restaurantId) {
        User owner = User.builder()
                .id(id)
                .email(email)
                .username(username)
                .passwordHash("stored-hash")
                .firstName(username.contains("bistro") ? "Bistro" : username.contains("pizza") ? "Pizza" : "Cafe")
                .lastName("Owner")
                .phone(username.contains("bistro") ? "+355690200001" : username.contains("pizza") ? "+355690200002" : "+355690200003")
                .restaurantId(restaurantId)
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .build();
        owner.setDeletedAt(null);
        return owner;
    }

    private Restaurant existingRestaurant(
            UUID id,
            String name,
            String legalName,
            String code,
            String slug,
            UUID ownerId
    ) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(id);
        restaurant.setName(name);
        restaurant.setLegalName(legalName);
        restaurant.setCode(code);
        restaurant.setSlug(slug);
        restaurant.setDescription(name.contains("Bistro")
                ? "Seeded local restaurant for menu and restaurant-linked development data."
                : name.contains("Pizza")
                ? "Seeded local restaurant with a second stable restaurant id for local testing."
                : "Seeded local restaurant for lightweight menu and reporting experiments.");
        restaurant.setEmail(name.contains("Bistro") ? "bistro@pos.local" : name.contains("Pizza") ? "pizza@pos.local" : "cafe@pos.local");
        restaurant.setPhone(name.contains("Bistro") ? "+355690100001" : name.contains("Pizza") ? "+355690100002" : "+355690100003");
        restaurant.setWebsite(name.contains("Bistro")
                ? "https://bistro.pos.local"
                : name.contains("Pizza")
                ? "https://pizza.pos.local"
                : "https://cafe.pos.local");
        restaurant.setCurrency(name.contains("Cafe") ? "USD" : "EUR");
        restaurant.setTimezone(name.contains("Bistro")
                ? "Europe/Tirane"
                : name.contains("Pizza")
                ? "Europe/Rome"
                : "America/New_York");
        restaurant.setOwnerId(ownerId);
        restaurant.setActive(true);
        restaurant.setStatus(RestaurantStatus.ACTIVE);
        return restaurant;
    }
}
