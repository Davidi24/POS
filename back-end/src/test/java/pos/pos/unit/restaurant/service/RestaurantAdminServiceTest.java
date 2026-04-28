package pos.pos.unit.restaurant.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import pos.pos.auth.enums.ClientLinkTarget;
import pos.pos.common.dto.PageResponse;
import pos.pos.exception.auth.AuthException;
import pos.pos.exception.restaurant.RestaurantAccessNotAllowedException;
import pos.pos.exception.restaurant.RestaurantCodeAlreadyExistsException;
import pos.pos.exception.restaurant.RestaurantDeletionNotAllowedException;
import pos.pos.exception.restaurant.RestaurantOwnershipChangeNotAllowedException;
import pos.pos.restaurant.dto.CreateRestaurantOwnerRequest;
import pos.pos.restaurant.dto.CreateRestaurantRequest;
import pos.pos.restaurant.dto.RestaurantResponse;
import pos.pos.restaurant.dto.UpdateRestaurantRequest;
import pos.pos.restaurant.dto.UpdateRestaurantStatusRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;
import pos.pos.restaurant.mapper.BranchMapper;
import pos.pos.restaurant.mapper.RestaurantMapper;
import pos.pos.restaurant.policy.RestaurantPolicy;
import pos.pos.restaurant.repository.BranchRepository;
import pos.pos.restaurant.repository.RestaurantRepository;
import pos.pos.restaurant.service.RestaurantAdminService;
import pos.pos.restaurant.service.RestaurantOwnerProvisioningService;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.restaurant.service.RestaurantValidationService;
import pos.pos.security.principal.AuthenticatedUser;
import pos.pos.security.scope.ActorScope;
import pos.pos.security.scope.ActorScopeService;
import pos.pos.user.entity.User;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestaurantAdminService")
class RestaurantAdminServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID TARGET_RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");

    @Mock
    private RestaurantRepository restaurantRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private ActorScopeService actorScopeService;

    @Mock
    private RestaurantPolicy restaurantPolicy;

    @Mock
    private RestaurantValidationService restaurantValidationService;

    @Mock
    private RestaurantOwnerProvisioningService restaurantOwnerProvisioningService;

    @Mock
    private RestaurantScopeService restaurantScopeService;

    @Spy
    private RestaurantMapper restaurantMapper = new RestaurantMapper();

    @Spy
    private BranchMapper branchMapper = new BranchMapper();

    @InjectMocks
    private RestaurantAdminService restaurantAdminService;

    private Authentication authentication() {
        return new UsernamePasswordAuthenticationToken(
                AuthenticatedUser.builder()
                        .id(ACTOR_ID)
                        .email("owner@pos.local")
                        .username("owner.main")
                        .active(true)
                        .build(),
                null,
                List.of()
        );
    }

    @Test
    @DisplayName("getRestaurants should return a paged response with actor scope and requested sorting")
    void shouldReturnPagedRestaurants() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant(TARGET_RESTAURANT_ID, OWNER_ID);

        given(actorScopeService.resolve(authentication)).willReturn(actorScope(false, ACTOR_RESTAURANT_ID));
        given(restaurantRepository.searchVisibleRestaurants(
                eq(true),
                eq(RestaurantStatus.ACTIVE),
                eq(OWNER_ID),
                eq("%pos%"),
                eq(false),
                eq(ACTOR_ID),
                eq(ACTOR_RESTAURANT_ID),
                any(Pageable.class)
        )).willReturn(new PageImpl<>(List.of(restaurant)));

        PageResponse<RestaurantResponse> response = restaurantAdminService.getRestaurants(
                authentication,
                "pos",
                true,
                "ACTIVE",
                OWNER_ID,
                0,
                10,
                "name",
                "asc"
        );

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        then(restaurantRepository).should().searchVisibleRestaurants(
                eq(true),
                eq(RestaurantStatus.ACTIVE),
                eq(OWNER_ID),
                eq("%pos%"),
                eq(false),
                eq(ACTOR_ID),
                eq(ACTOR_RESTAURANT_ID),
                pageableCaptor.capture()
        );

        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageNumber()).isEqualTo(0);
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable.getSort().getOrderFor("name")).isNotNull();
        assertThat(pageable.getSort().getOrderFor("name").getDirection().name()).isEqualTo("ASC");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getId()).isEqualTo(TARGET_RESTAURANT_ID);
    }

    @Test
    @DisplayName("getRestaurants should reject unsupported sortBy values")
    void shouldRejectUnsupportedSortBy() {
        Authentication authentication = authentication();

        assertThatThrownBy(() -> restaurantAdminService.getRestaurants(
                authentication,
                null,
                null,
                null,
                null,
                0,
                20,
                "passwordHash",
                "desc"
        ))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid sortBy value");
    }

    @Test
    @DisplayName("createRestaurant should create a normalized restaurant")
    void shouldCreateNormalizedRestaurant() {
        Authentication authentication = authentication();

        CreateRestaurantRequest request = CreateRestaurantRequest.builder()
                .name(" POS Main ")
                .legalName("POS Main LLC")
                .currency("usd")
                .timezone("Europe/Berlin")
                .owner(CreateRestaurantOwnerRequest.builder()
                        .email("OWNER@POS.LOCAL")
                        .username("Owner.Main")
                        .firstName("Owner")
                        .lastName("Main")
                        .phone("+355 69 123 4567")
                        .clientTarget(ClientLinkTarget.WEB)
                        .build())
                .build();

        User owner = User.builder()
                .id(OWNER_ID)
                .email("owner@pos.local")
                .username("owner.main")
                .restaurantId(TARGET_RESTAURANT_ID)
                .passwordHash("hashed-password")
                .firstName("Owner")
                .lastName("Main")
                .isActive(true)
                .emailVerified(false)
                .build();

        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantValidationService.normalizeAndValidateFields(
                request.getCode(),
                request.getSlug(),
                request.getName(),
                request.getTimezone(),
                null
        )).willReturn(new RestaurantValidationService.NormalizedRestaurantFields("POS_MAIN", "pos-main"));
        given(restaurantRepository.save(any(Restaurant.class))).willAnswer(invocation -> {
            Restaurant saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(TARGET_RESTAURANT_ID);
                saved.setCreatedAt(OffsetDateTime.parse("2026-04-22T10:15:30Z"));
                saved.setUpdatedAt(saved.getCreatedAt());
            }
            return saved;
        });
        given(restaurantOwnerProvisioningService.createAndInviteOwner(
                request.getOwner(),
                TARGET_RESTAURANT_ID,
                ACTOR_ID,
                " POS Main "
        )).willReturn(owner);

        RestaurantResponse response = restaurantAdminService.createRestaurant(authentication, request);

        ArgumentCaptor<Restaurant> restaurantCaptor = ArgumentCaptor.forClass(Restaurant.class);
        verify(restaurantRepository, times(2)).save(restaurantCaptor.capture());
        Restaurant finalSaved = restaurantCaptor.getAllValues().get(1);
        assertThat(finalSaved.getOwnerId()).isEqualTo(OWNER_ID);
        assertThat(finalSaved.getCode()).isEqualTo("POS_MAIN");
        assertThat(finalSaved.getSlug()).isEqualTo("pos-main");
        assertThat(finalSaved.isActive()).isTrue();
        assertThat(finalSaved.getStatus()).isEqualTo(RestaurantStatus.ACTIVE);
        assertThat(finalSaved.getCreatedBy()).isEqualTo(ACTOR_ID);

        assertThat(response.getId()).isEqualTo(TARGET_RESTAURANT_ID);
        assertThat(response.getCode()).isEqualTo("POS_MAIN");
        assertThat(response.getSlug()).isEqualTo("pos-main");
        assertThat(response.getOwnerUserId()).isEqualTo(OWNER_ID);
        assertThat(response.getIsActive()).isTrue();
        assertThat(response.getStatus()).isEqualTo(RestaurantStatus.ACTIVE);
        verify(restaurantOwnerProvisioningService).createAndInviteOwner(
                request.getOwner(),
                TARGET_RESTAURANT_ID,
                ACTOR_ID,
                " POS Main "
        );
    }

    @Test
    @DisplayName("createRestaurant should reject duplicate normalized codes")
    void shouldRejectDuplicateCodes() {
        Authentication authentication = authentication();

        CreateRestaurantRequest request = CreateRestaurantRequest.builder()
                .name("POS Main")
                .legalName("POS Main LLC")
                .currency("USD")
                .timezone("Europe/Berlin")
                .owner(CreateRestaurantOwnerRequest.builder()
                        .email("owner@pos.local")
                        .username("owner.main")
                        .firstName("Owner")
                        .lastName("Main")
                        .build())
                .build();

        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantValidationService.normalizeAndValidateFields(
                request.getCode(),
                request.getSlug(),
                request.getName(),
                request.getTimezone(),
                null
        )).willThrow(new RestaurantCodeAlreadyExistsException());

        assertThatThrownBy(() -> restaurantAdminService.createRestaurant(authentication, request))
                .isInstanceOf(RestaurantCodeAlreadyExistsException.class);

        verify(restaurantRepository, never()).save(any(Restaurant.class));
    }

    @Test
    @DisplayName("getRestaurant should reject inaccessible restaurants")
    void shouldRejectInaccessibleRestaurant() {
        Authentication authentication = authentication();
        given(restaurantScopeService.requireAccessibleRestaurant(authentication, TARGET_RESTAURANT_ID))
                .willThrow(new RestaurantAccessNotAllowedException());

        assertThatThrownBy(() -> restaurantAdminService.getRestaurant(authentication, TARGET_RESTAURANT_ID))
                .isInstanceOf(RestaurantAccessNotAllowedException.class);
    }

    @Test
    @DisplayName("updateRestaurant should reject ownership changes by non-super-admin actors")
    void shouldRejectOwnershipChangesByNonSuperAdmin() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant(TARGET_RESTAURANT_ID, OWNER_ID);

        UpdateRestaurantRequest request = UpdateRestaurantRequest.builder()
                .name("POS Main")
                .legalName("POS Main LLC")
                .code("POS_MAIN")
                .slug("pos-main")
                .currency("USD")
                .timezone("Europe/Berlin")
                .ownerUserId(null)
                .isActive(true)
                .status(RestaurantStatus.ACTIVE)
                .build();

        given(actorScopeService.resolve(authentication)).willReturn(actorScope(false, ACTOR_RESTAURANT_ID));
        given(restaurantScopeService.requireManageableRestaurant(any(ActorScope.class), eq(TARGET_RESTAURANT_ID)))
                .willReturn(restaurant);
        org.mockito.Mockito.doThrow(new RestaurantOwnershipChangeNotAllowedException())
                .when(restaurantPolicy).assertCanChangeOwner(any(ActorScope.class), eq(restaurant), eq(null));

        assertThatThrownBy(() -> restaurantAdminService.updateRestaurant(authentication, TARGET_RESTAURANT_ID, request))
                .isInstanceOf(RestaurantOwnershipChangeNotAllowedException.class);
    }

    @Test
    @DisplayName("updateRestaurantStatus should validate active and status consistency")
    void shouldValidateStatusConsistency() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant(TARGET_RESTAURANT_ID, OWNER_ID);

        UpdateRestaurantStatusRequest request = new UpdateRestaurantStatusRequest();
        request.setIsActive(true);
        request.setStatus(RestaurantStatus.SUSPENDED);

        given(restaurantScopeService.requireManageableRestaurant(authentication, TARGET_RESTAURANT_ID))
                .willReturn(restaurant);
        org.mockito.Mockito.doThrow(new AuthException(
                "Non-active restaurant statuses must have isActive=false",
                HttpStatus.BAD_REQUEST
        )).when(restaurantValidationService).validateStatusConsistency(true, RestaurantStatus.SUSPENDED);

        assertThatThrownBy(() -> restaurantAdminService.updateRestaurantStatus(authentication, TARGET_RESTAURANT_ID, request))
                .isInstanceOf(AuthException.class)
                .hasMessage("Non-active restaurant statuses must have isActive=false");
    }

    @Test
    @DisplayName("deleteRestaurant should archive and soft delete the restaurant for super admin")
    void shouldSoftDeleteRestaurant() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant(TARGET_RESTAURANT_ID, OWNER_ID);

        given(restaurantScopeService.requireExistingRestaurant(TARGET_RESTAURANT_ID)).willReturn(restaurant);
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);

        restaurantAdminService.deleteRestaurant(authentication, TARGET_RESTAURANT_ID);

        assertThat(restaurant.isActive()).isFalse();
        assertThat(restaurant.getStatus()).isEqualTo(RestaurantStatus.ARCHIVED);
        assertThat(restaurant.getDeletedAt()).isNotNull();
        assertThat(restaurant.getUpdatedBy()).isEqualTo(ACTOR_ID);
        verify(restaurantRepository).save(restaurant);
    }

    @Test
    @DisplayName("deleteRestaurant should cascade soft-delete active branches via bulk UPDATE")
    void shouldCascadeSoftDeleteBranches() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant(TARGET_RESTAURANT_ID, OWNER_ID);

        given(restaurantScopeService.requireExistingRestaurant(TARGET_RESTAURANT_ID)).willReturn(restaurant);
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);

        restaurantAdminService.deleteRestaurant(authentication, TARGET_RESTAURANT_ID);

        verify(branchRepository).softDeleteAllByRestaurantId(eq(TARGET_RESTAURANT_ID), any(), eq(ACTOR_ID));
        verify(restaurantRepository).save(restaurant);
    }

    @Test
    @DisplayName("deleteRestaurant should reject non-super-admin actors")
    void shouldRejectDeleteForNonSuperAdmin() {
        Authentication authentication = authentication();
        org.mockito.Mockito.doThrow(new RestaurantDeletionNotAllowedException())
                .when(restaurantScopeService).assertCanDeleteRestaurant(authentication);

        assertThatThrownBy(() -> restaurantAdminService.deleteRestaurant(authentication, TARGET_RESTAURANT_ID))
                .isInstanceOf(RestaurantDeletionNotAllowedException.class);

        verify(restaurantRepository, never()).save(any(Restaurant.class));
    }

    private User user(UUID userId, UUID restaurantId, boolean active) {
        return User.builder()
                .id(userId)
                .email("owner@pos.local")
                .username("owner")
                .passwordHash("stored-hash")
                .firstName("Owner")
                .lastName("User")
                .status("ACTIVE")
                .isActive(active)
                .restaurantId(restaurantId)
                .build();
    }

    private ActorScope actorScope(boolean superAdmin, UUID restaurantId) {
        return new ActorScope(
                user(ACTOR_ID, restaurantId, true),
                superAdmin,
                Set.of(superAdmin ? "SUPER_ADMIN" : "OWNER"),
                Set.of("RESTAURANTS_READ", "RESTAURANTS_UPDATE")
        );
    }

    private Restaurant restaurant(UUID restaurantId, UUID ownerId) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(restaurantId);
        restaurant.setName("POS Main");
        restaurant.setLegalName("POS Main LLC");
        restaurant.setCode("POS_MAIN");
        restaurant.setSlug("pos-main");
        restaurant.setCurrency("USD");
        restaurant.setTimezone("Europe/Berlin");
        restaurant.setActive(true);
        restaurant.setStatus(RestaurantStatus.ACTIVE);
        restaurant.setOwnerId(ownerId);
        return restaurant;
    }
}
