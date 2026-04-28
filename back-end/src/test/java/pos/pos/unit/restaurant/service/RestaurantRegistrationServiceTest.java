package pos.pos.unit.restaurant.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.exception.restaurant.RestaurantRegistrationNotPendingException;
import pos.pos.exception.restaurant.RestaurantReviewNotAllowedException;
import pos.pos.restaurant.dto.CreateRestaurantOwnerRequest;
import pos.pos.restaurant.dto.RestaurantResponse;
import pos.pos.restaurant.dto.RestaurantRegistrationRequest;
import pos.pos.restaurant.dto.ReviewRestaurantRegistrationRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantRegistrationDecision;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.mapper.RestaurantMapper;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.restaurant.service.RestaurantOwnerProvisioningService;
import pos.pos.restaurant.service.RestaurantRegistrationService;
import pos.pos.restaurant.service.RestaurantValidationService;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.rbac.RoleHierarchyService;
import pos.pos.user.entity.User;
import pos.pos.user.service.UserIdentityService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestaurantRegistrationService")
class RestaurantRegistrationServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private RestaurantValidationService restaurantValidationService;

    @Mock
    private RestaurantOwnerProvisioningService restaurantOwnerProvisioningService;

    @Mock
    private UserIdentityService userIdentityService;

    @Mock
    private RoleHierarchyService roleHierarchyService;

    @Spy
    private RestaurantMapper restaurantMapper = new RestaurantMapper();

    @InjectMocks
    private RestaurantRegistrationService restaurantRegistrationService;

    @Test
    @DisplayName("registerRestaurant should create a pending registration with owner snapshot")
    void shouldRegisterPendingRestaurant() {
        RestaurantRegistrationRequest request = request();

        given(restaurantValidationService.normalizeAndGenerateUniqueRegistrationFields(
                request.getName(),
                request.getTimezone()
        )).willReturn(new RestaurantValidationService.NormalizedRestaurantFields("BH001", "burger-house"));
        given(restaurantRepository.save(any(Restaurant.class))).willAnswer(invocation -> {
            Restaurant restaurant = invocation.getArgument(0);
            restaurant.setId(RESTAURANT_ID);
            restaurant.setCreatedAt(OffsetDateTime.parse("2026-04-23T10:00:00Z"));
            restaurant.setUpdatedAt(restaurant.getCreatedAt());
            return restaurant;
        });

        RestaurantResponse response = restaurantRegistrationService.registerRestaurant(request);

        assertThat(response.getId()).isEqualTo(RESTAURANT_ID);
        assertThat(response.getStatus()).isEqualTo(RestaurantStatus.PENDING);
        assertThat(response.getIsActive()).isFalse();
        assertThat(response.getOwnerUserId()).isNull();

        verify(userIdentityService).normalizeAndAssertUnique(
                "owner@burger.house",
                "burger.owner",
                "+1987654321"
        );
    }

    @Test
    @DisplayName("reviewRegistration should approve a pending registration and create the owner")
    void shouldApprovePendingRegistration() {
        Authentication authentication = authentication();
        ReviewRestaurantRegistrationRequest request = new ReviewRestaurantRegistrationRequest();
        request.setDecision(RestaurantRegistrationDecision.APPROVE);

        Restaurant restaurant = pendingRestaurant();
        User owner = User.builder()
                .id(OWNER_ID)
                .email("owner@burger.house")
                .username("burger.owner")
                .restaurantId(RESTAURANT_ID)
                .build();

        given(roleHierarchyService.isSuperAdmin(authentication)).willReturn(true);
        given(roleHierarchyService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantRepository.findByIdAndDeletedAtIsNull(RESTAURANT_ID)).willReturn(Optional.of(restaurant));
        given(restaurantOwnerProvisioningService.createAndInvitePendingOwner(restaurant, ACTOR_ID, "Burger House"))
                .willReturn(owner);

        RestaurantResponse response = restaurantRegistrationService.reviewRegistration(authentication, RESTAURANT_ID, request);

        assertThat(response.getOwnerUserId()).isEqualTo(OWNER_ID);
        assertThat(response.getStatus()).isEqualTo(RestaurantStatus.ACTIVE);
        assertThat(response.getIsActive()).isTrue();
        assertThat(restaurant.getPendingOwnerEmail()).isNull();
        assertThat(restaurant.getPendingOwnerUsername()).isNull();
        verify(restaurantRepository).save(restaurant);
    }

    @Test
    @DisplayName("reviewRegistration should reject a pending registration without creating a user")
    void shouldRejectPendingRegistration() {
        Authentication authentication = authentication();
        ReviewRestaurantRegistrationRequest request = new ReviewRestaurantRegistrationRequest();
        request.setDecision(RestaurantRegistrationDecision.REJECT);

        Restaurant restaurant = pendingRestaurant();

        given(roleHierarchyService.isSuperAdmin(authentication)).willReturn(true);
        given(roleHierarchyService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantRepository.findByIdAndDeletedAtIsNull(RESTAURANT_ID)).willReturn(Optional.of(restaurant));

        RestaurantResponse response = restaurantRegistrationService.reviewRegistration(authentication, RESTAURANT_ID, request);

        assertThat(response.getStatus()).isEqualTo(RestaurantStatus.REJECTED);
        assertThat(response.getIsActive()).isFalse();
        verify(restaurantOwnerProvisioningService, never()).createAndInvitePendingOwner(any(), any(), any());
        verify(restaurantRepository).save(restaurant);
    }

    @Test
    @DisplayName("reviewRegistration should reject non-super-admin actors")
    void shouldRejectReviewByNonSuperAdmin() {
        Authentication authentication = authentication();
        ReviewRestaurantRegistrationRequest request = new ReviewRestaurantRegistrationRequest();
        request.setDecision(RestaurantRegistrationDecision.APPROVE);

        given(roleHierarchyService.isSuperAdmin(authentication)).willReturn(false);

        assertThatThrownBy(() -> restaurantRegistrationService.reviewRegistration(authentication, RESTAURANT_ID, request))
                .isInstanceOf(RestaurantReviewNotAllowedException.class);
    }

    @Test
    @DisplayName("reviewRegistration should reject already reviewed registrations")
    void shouldRejectAlreadyReviewedRegistration() {
        Authentication authentication = authentication();
        ReviewRestaurantRegistrationRequest request = new ReviewRestaurantRegistrationRequest();
        request.setDecision(RestaurantRegistrationDecision.APPROVE);

        Restaurant restaurant = pendingRestaurant();
        restaurant.setStatus(RestaurantStatus.REJECTED);

        given(roleHierarchyService.isSuperAdmin(authentication)).willReturn(true);
        given(restaurantRepository.findByIdAndDeletedAtIsNull(RESTAURANT_ID)).willReturn(Optional.of(restaurant));

        assertThatThrownBy(() -> restaurantRegistrationService.reviewRegistration(authentication, RESTAURANT_ID, request))
                .isInstanceOf(RestaurantRegistrationNotPendingException.class);
    }

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(ACTOR_ID)
                        .email("admin@pos.local")
                        .username("admin")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    private RestaurantRegistrationRequest request() {
        return RestaurantRegistrationRequest.builder()
                .name("Burger House")
                .legalName("Burger House LLC")
                .description("Burger spot")
                .email("hello@burger.house")
                .phone("+1234567890")
                .website("https://burger.house")
                .currency("USD")
                .timezone("America/New_York")
                .owner(CreateRestaurantOwnerRequest.builder()
                        .email("owner@burger.house")
                        .username("burger.owner")
                        .firstName("Burger")
                        .lastName("Owner")
                        .phone("+1987654321")
                        .build())
                .build();
    }

    private Restaurant pendingRestaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setName("Burger House");
        restaurant.setLegalName("Burger House LLC");
        restaurant.setCode("BH001");
        restaurant.setSlug("burger-house");
        restaurant.setCurrency("USD");
        restaurant.setTimezone("America/New_York");
        restaurant.setStatus(RestaurantStatus.PENDING);
        restaurant.setActive(false);
        restaurant.setPendingOwnerEmail("owner@burger.house");
        restaurant.setPendingOwnerUsername("burger.owner");
        restaurant.setPendingOwnerFirstName("Burger");
        restaurant.setPendingOwnerLastName("Owner");
        restaurant.setPendingOwnerPhone("+1987654321");
        return restaurant;
    }
}
