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
import pos.pos.exception.auth.AuthException;
import pos.pos.restaurant.dto.RestaurantTaxProfileResponse;
import pos.pos.restaurant.dto.UpsertRestaurantTaxProfileRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.entity.RestaurantTaxProfile;
import pos.pos.restaurant.mapper.RestaurantTaxProfileMapper;
import pos.pos.restaurant.repository.RestaurantTaxProfileRepository;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.restaurant.service.RestaurantTaxProfileService;
import pos.pos.security.principal.AuthenticatedUser;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestaurantTaxProfileService")
class RestaurantTaxProfileServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID TAX_PROFILE_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");

    @Mock
    private RestaurantScopeService restaurantScopeService;

    @Mock
    private RestaurantTaxProfileRepository restaurantTaxProfileRepository;

    @Spy
    private RestaurantTaxProfileMapper restaurantTaxProfileMapper = new RestaurantTaxProfileMapper();

    @InjectMocks
    private RestaurantTaxProfileService restaurantTaxProfileService;

    @Test
    @DisplayName("createTaxProfile should call clearDefault when new profile is default")
    void shouldClearExistingDefault() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();

        UpsertRestaurantTaxProfileRequest request = UpsertRestaurantTaxProfileRequest.builder()
                .country("Albania")
                .taxNumber("TN-123")
                .isDefault(true)
                .build();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant);
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantTaxProfileRepository.save(any(RestaurantTaxProfile.class))).willAnswer(invocation -> {
            RestaurantTaxProfile taxProfile = invocation.getArgument(0);
            if (taxProfile.getId() == null) {
                taxProfile.setId(UUID.fromString("00000000-0000-0000-0000-000000000021"));
            }
            return taxProfile;
        });

        RestaurantTaxProfileResponse response = restaurantTaxProfileService.createTaxProfile(authentication, RESTAURANT_ID, request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getIsDefault()).isTrue();
        verify(restaurantTaxProfileRepository).clearDefault(RESTAURANT_ID, null, ACTOR_ID);
    }

    @Test
    @DisplayName("createTaxProfile should reject invalid effective ranges")
    void shouldRejectInvalidEffectiveRange() {
        Authentication authentication = authentication();
        UpsertRestaurantTaxProfileRequest request = UpsertRestaurantTaxProfileRequest.builder()
                .country("Albania")
                .effectiveFrom(OffsetDateTime.parse("2026-05-01T00:00:00Z"))
                .effectiveTo(OffsetDateTime.parse("2026-04-01T00:00:00Z"))
                .build();

        assertThatThrownBy(() -> restaurantTaxProfileService.createTaxProfile(authentication, RESTAURANT_ID, request))
                .isInstanceOf(AuthException.class)
                .hasMessage("effectiveTo must be after effectiveFrom");
    }

    @Test
    @DisplayName("makeDefault should call clearDefault and set target as default")
    void shouldReplaceExistingDefaultProfile() {
        Authentication authentication = authentication();
        RestaurantTaxProfile target = taxProfile(TAX_PROFILE_ID, false);

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantTaxProfileRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(TAX_PROFILE_ID, RESTAURANT_ID))
                .willReturn(Optional.of(target));

        RestaurantTaxProfileResponse response = restaurantTaxProfileService.makeDefault(authentication, RESTAURANT_ID, TAX_PROFILE_ID);

        assertThat(target.isDefault()).isTrue();
        assertThat(response.getId()).isEqualTo(TAX_PROFILE_ID);
        verify(restaurantTaxProfileRepository).clearDefault(RESTAURANT_ID, TAX_PROFILE_ID, ACTOR_ID);
        verify(restaurantTaxProfileRepository).save(target);
    }

    @Test
    @DisplayName("updateTaxProfile should call clearDefault and update fields")
    void shouldUpdateTaxProfile() {
        Authentication authentication = authentication();
        RestaurantTaxProfile target = taxProfile(TAX_PROFILE_ID, false);

        UpsertRestaurantTaxProfileRequest request = UpsertRestaurantTaxProfileRequest.builder()
                .country("Kosovo")
                .taxNumber("TN-999")
                .isDefault(true)
                .build();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantTaxProfileRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(TAX_PROFILE_ID, RESTAURANT_ID))
                .willReturn(Optional.of(target));
        given(restaurantTaxProfileRepository.save(any(RestaurantTaxProfile.class))).willAnswer(i -> i.getArgument(0));

        RestaurantTaxProfileResponse response = restaurantTaxProfileService.updateTaxProfile(
                authentication, RESTAURANT_ID, TAX_PROFILE_ID, request);

        assertThat(target.isDefault()).isTrue();
        assertThat(response.getId()).isEqualTo(TAX_PROFILE_ID);
        verify(restaurantTaxProfileRepository).clearDefault(RESTAURANT_ID, TAX_PROFILE_ID, ACTOR_ID);
    }

    @Test
    @DisplayName("deleteTaxProfile should soft-delete an existing tax profile")
    void shouldSoftDeleteTaxProfile() {
        Authentication authentication = authentication();
        RestaurantTaxProfile target = taxProfile(TAX_PROFILE_ID, false);

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantTaxProfileRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(TAX_PROFILE_ID, RESTAURANT_ID))
                .willReturn(Optional.of(target));
        given(restaurantTaxProfileRepository.save(target)).willReturn(target);

        restaurantTaxProfileService.deleteTaxProfile(authentication, RESTAURANT_ID, TAX_PROFILE_ID);

        assertThat(target.getDeletedAt()).isNotNull();
        assertThat(target.isDefault()).isFalse();
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

    private Restaurant restaurant() {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        restaurant.setName("POS Main");
        return restaurant;
    }

    private RestaurantTaxProfile taxProfile(UUID taxProfileId, boolean isDefault) {
        RestaurantTaxProfile taxProfile = new RestaurantTaxProfile();
        taxProfile.setId(taxProfileId);
        taxProfile.setRestaurant(restaurant());
        taxProfile.setCountry("Albania");
        taxProfile.setTaxNumber("TN-123");
        taxProfile.setDefault(isDefault);
        taxProfile.setCreatedAt(OffsetDateTime.parse("2026-04-23T10:00:00Z"));
        taxProfile.setUpdatedAt(taxProfile.getCreatedAt());
        return taxProfile;
    }
}
