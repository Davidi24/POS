package pos.pos.restaurant.bootstrap;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.role.RoleNotFoundException;
import pos.pos.role.entity.Role;
import pos.pos.role.repository.RoleRepository;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.security.rbac.AppRole;
import pos.pos.security.service.PasswordService;
import pos.pos.user.entity.User;
import pos.pos.user.entity.UserRole;
import pos.pos.user.repository.UserRepository;
import pos.pos.user.repository.UserRoleRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Component
@Order(100)
@Profile("local")
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "app.bootstrap.sample-restaurants.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LocalRestaurantSeedRunner implements CommandLineRunner {

    public static final UUID DEMO_BISTRO_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    public static final UUID DEMO_PIZZA_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    public static final UUID DEMO_CAFE_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");

    public static final UUID DEMO_BISTRO_OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000101");
    public static final UUID DEMO_PIZZA_OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000102");
    public static final UUID DEMO_CAFE_OWNER_ID = UUID.fromString("10000000-0000-0000-0000-000000000103");

    private static final Logger logger = LoggerFactory.getLogger(LocalRestaurantSeedRunner.class);
    private static final String DEMO_OWNER_PASSWORD = "LocalDemo123!";

    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final PasswordService passwordService;

    @Override
    @Transactional
    public void run(String... args) {
        Role ownerRole = roleRepository.findByCode(AppRole.OWNER.name())
                .filter(Role::isActive)
                .orElseThrow(RoleNotFoundException::new);

        List<SeededRestaurant> restaurants = sampleRestaurants().stream()
                .map(spec -> seedRestaurant(spec, ownerRole))
                .toList();

        logger.info("Local sample restaurants ready. Use these ids for menu-related development data:");
        restaurants.forEach(seed -> logger.info(
                " - {} | {} | slug={} | code={} | owner={}",
                seed.restaurant().getId(),
                seed.restaurant().getName(),
                seed.restaurant().getSlug(),
                seed.restaurant().getCode(),
                seed.owner().getUsername()
        ));
    }

    private SeededRestaurant seedRestaurant(SampleRestaurantSpec spec, Role ownerRole) {
        User owner = ensureOwner(spec.owner(), spec.id(), ownerRole);
        Restaurant restaurant = ensureRestaurant(spec, owner.getId());
        if (!Objects.equals(owner.getRestaurantId(), restaurant.getId())) {
            owner.setRestaurantId(restaurant.getId());
            owner = userRepository.save(owner);
        }

        return new SeededRestaurant(restaurant, owner);
    }

    private User ensureOwner(SampleOwnerSpec spec, UUID restaurantId, Role ownerRole) {
        User owner = userRepository.findById(spec.id())
                .or(() -> userRepository.findByEmailAndDeletedAtIsNull(spec.email()))
                .or(() -> userRepository.findByUsernameAndDeletedAtIsNull(spec.username()))
                .map(existing -> restoreOwnerIfNeeded(existing, spec, restaurantId))
                .orElseGet(() -> createOwner(spec, restaurantId));

        if (!userRoleRepository.existsByUserIdAndRoleId(owner.getId(), ownerRole.getId())) {
            userRoleRepository.save(UserRole.builder()
                    .userId(owner.getId())
                    .roleId(ownerRole.getId())
                    .build());
        }

        return owner;
    }

    private User restoreOwnerIfNeeded(User owner, SampleOwnerSpec spec, UUID restaurantId) {
        if (isSeedOwnerReady(owner, spec, restaurantId)) {
            return owner;
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        owner.setEmail(spec.email());
        owner.setUsername(spec.username());
        owner.setFirstName(spec.firstName());
        owner.setLastName(spec.lastName());
        owner.setPhone(spec.phone());
        owner.setRestaurantId(restaurantId);
        owner.setStatus("ACTIVE");
        owner.setActive(true);
        owner.setDeletedAt(null);
        owner.setEmailVerified(true);
        if (owner.getEmailVerifiedAt() == null) {
            owner.setEmailVerifiedAt(now);
        }
        owner.setPhoneVerified(spec.phone() != null);
        if (spec.phone() != null && owner.getPhoneVerifiedAt() == null) {
            owner.setPhoneVerifiedAt(now);
        }
        if (owner.getPasswordHash() == null || owner.getPasswordHash().isBlank()) {
            owner.setPasswordHash(passwordService.hash(DEMO_OWNER_PASSWORD));
        }
        if (owner.getPasswordUpdatedAt() == null) {
            owner.setPasswordUpdatedAt(now);
        }

        return userRepository.save(owner);
    }

    private boolean isSeedOwnerReady(User owner, SampleOwnerSpec spec, UUID restaurantId) {
        return owner.getDeletedAt() == null
                && owner.isActive()
                && "ACTIVE".equals(owner.getStatus())
                && Objects.equals(owner.getEmail(), spec.email())
                && Objects.equals(owner.getUsername(), spec.username())
                && Objects.equals(owner.getFirstName(), spec.firstName())
                && Objects.equals(owner.getLastName(), spec.lastName())
                && Objects.equals(owner.getPhone(), spec.phone())
                && Objects.equals(owner.getRestaurantId(), restaurantId)
                && owner.isEmailVerified();
    }

    private User createOwner(SampleOwnerSpec spec, UUID restaurantId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return userRepository.save(User.builder()
                .id(spec.id())
                .email(spec.email())
                .username(spec.username())
                .passwordHash(passwordService.hash(DEMO_OWNER_PASSWORD))
                .firstName(spec.firstName())
                .lastName(spec.lastName())
                .phone(spec.phone())
                .restaurantId(restaurantId)
                .status("ACTIVE")
                .isActive(true)
                .emailVerified(true)
                .emailVerifiedAt(now)
                .phoneVerified(spec.phone() != null)
                .phoneVerifiedAt(spec.phone() != null ? now : null)
                .failedLoginAttempts(0)
                .pinEnabled(false)
                .pinAttempts(0)
                .passwordUpdatedAt(now)
                .build());
    }

    private Restaurant ensureRestaurant(SampleRestaurantSpec spec, UUID ownerId) {
        return restaurantRepository.findById(spec.id())
                .map(existing -> restoreRestaurantIfNeeded(existing, spec, ownerId))
                .or(() -> restaurantRepository.findBySlugAndDeletedAtIsNull(spec.slug())
                        .map(existing -> restoreRestaurantIfNeeded(existing, spec, ownerId)))
                .orElseGet(() -> createRestaurant(spec, ownerId));
    }

    private Restaurant restoreRestaurantIfNeeded(
            Restaurant restaurant,
            SampleRestaurantSpec spec,
            UUID ownerId
    ) {
        if (isSeedRestaurantReady(restaurant, spec, ownerId)) {
            return restaurant;
        }

        restaurant.setName(spec.name());
        restaurant.setLegalName(spec.legalName());
        restaurant.setCode(spec.code());
        restaurant.setSlug(spec.slug());
        restaurant.setDescription(spec.description());
        restaurant.setEmail(spec.email());
        restaurant.setPhone(spec.phone());
        restaurant.setWebsite(spec.website());
        restaurant.setCurrency(spec.currency());
        restaurant.setTimezone(spec.timezone());
        restaurant.setOwnerId(ownerId);
        restaurant.setDeletedAt(null);
        restaurant.setActive(true);
        restaurant.setStatus(RestaurantStatus.ACTIVE);

        return restaurantRepository.save(restaurant);
    }

    private boolean isSeedRestaurantReady(Restaurant restaurant, SampleRestaurantSpec spec, UUID ownerId) {
        return restaurant.getDeletedAt() == null
                && restaurant.isActive()
                && restaurant.getStatus() == RestaurantStatus.ACTIVE
                && Objects.equals(restaurant.getId(), spec.id())
                && Objects.equals(restaurant.getName(), spec.name())
                && Objects.equals(restaurant.getLegalName(), spec.legalName())
                && Objects.equals(restaurant.getCode(), spec.code())
                && Objects.equals(restaurant.getSlug(), spec.slug())
                && Objects.equals(restaurant.getDescription(), spec.description())
                && Objects.equals(restaurant.getEmail(), spec.email())
                && Objects.equals(restaurant.getPhone(), spec.phone())
                && Objects.equals(restaurant.getWebsite(), spec.website())
                && Objects.equals(restaurant.getCurrency(), spec.currency())
                && Objects.equals(restaurant.getTimezone(), spec.timezone())
                && Objects.equals(restaurant.getOwnerId(), ownerId);
    }

    private Restaurant createRestaurant(SampleRestaurantSpec spec, UUID ownerId) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(spec.id());
        restaurant.setName(spec.name());
        restaurant.setLegalName(spec.legalName());
        restaurant.setCode(spec.code());
        restaurant.setSlug(spec.slug());
        restaurant.setDescription(spec.description());
        restaurant.setEmail(spec.email());
        restaurant.setPhone(spec.phone());
        restaurant.setWebsite(spec.website());
        restaurant.setCurrency(spec.currency());
        restaurant.setTimezone(spec.timezone());
        restaurant.setOwnerId(ownerId);
        restaurant.setActive(true);
        restaurant.setStatus(RestaurantStatus.ACTIVE);
        return restaurantRepository.save(restaurant);
    }

    private List<SampleRestaurantSpec> sampleRestaurants() {
        return List.of(
                new SampleRestaurantSpec(
                        DEMO_BISTRO_ID,
                        "Local Demo Bistro",
                        "Local Demo Bistro LLC",
                        "LOCAL_DEMO_BISTRO",
                        "local-demo-bistro",
                        "Seeded local restaurant for menu and restaurant-linked development data.",
                        "bistro@pos.local",
                        "+355690100001",
                        "https://bistro.pos.local",
                        "EUR",
                        "Europe/Tirane",
                        new SampleOwnerSpec(
                                DEMO_BISTRO_OWNER_ID,
                                "bistro.owner@pos.local",
                                "demo.bistro.owner",
                                "Bistro",
                                "Owner",
                                "+355690200001"
                        )
                ),
                new SampleRestaurantSpec(
                        DEMO_PIZZA_ID,
                        "Local Demo Pizza",
                        "Local Demo Pizza LLC",
                        "LOCAL_DEMO_PIZZA",
                        "local-demo-pizza",
                        "Seeded local restaurant with a second stable restaurant id for local testing.",
                        "pizza@pos.local",
                        "+355690100002",
                        "https://pizza.pos.local",
                        "EUR",
                        "Europe/Rome",
                        new SampleOwnerSpec(
                                DEMO_PIZZA_OWNER_ID,
                                "pizza.owner@pos.local",
                                "demo.pizza.owner",
                                "Pizza",
                                "Owner",
                                "+355690200002"
                        )
                ),
                new SampleRestaurantSpec(
                        DEMO_CAFE_ID,
                        "Local Demo Cafe",
                        "Local Demo Cafe LLC",
                        "LOCAL_DEMO_CAFE",
                        "local-demo-cafe",
                        "Seeded local restaurant for lightweight menu and reporting experiments.",
                        "cafe@pos.local",
                        "+355690100003",
                        "https://cafe.pos.local",
                        "USD",
                        "America/New_York",
                        new SampleOwnerSpec(
                                DEMO_CAFE_OWNER_ID,
                                "cafe.owner@pos.local",
                                "demo.cafe.owner",
                                "Cafe",
                                "Owner",
                                "+355690200003"
                        )
                )
        );
    }

    private record SeededRestaurant(Restaurant restaurant, User owner) {
    }

    private record SampleRestaurantSpec(
            UUID id,
            String name,
            String legalName,
            String code,
            String slug,
            String description,
            String email,
            String phone,
            String website,
            String currency,
            String timezone,
            SampleOwnerSpec owner
    ) {
    }

    private record SampleOwnerSpec(
            UUID id,
            String email,
            String username,
            String firstName,
            String lastName,
            String phone
    ) {
    }
}
