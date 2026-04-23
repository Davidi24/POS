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

    @Mock
    private RestaurantScopeService restaurantScopeService;

    @Mock
    private RestaurantTaxProfileRepository restaurantTaxProfileRepository;

    @Spy
    private RestaurantTaxProfileMapper restaurantTaxProfileMapper = new RestaurantTaxProfileMapper();

    @InjectMocks
    private RestaurantTaxProfileService restaurantTaxProfileService;

    @Test
    @DisplayName("createTaxProfile should clear an existing default profile")
    void shouldClearExistingDefault() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();
        RestaurantTaxProfile existingDefault = new RestaurantTaxProfile();
        existingDefault.setId(UUID.fromString("00000000-0000-0000-0000-000000000020"));
        existingDefault.setDefault(true);

        UpsertRestaurantTaxProfileRequest request = UpsertRestaurantTaxProfileRequest.builder()
                .country("Albania")
                .taxNumber("TN-123")
                .isDefault(true)
                .build();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant);
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantTaxProfileRepository.findByRestaurantIdAndIsDefaultTrueAndDeletedAtIsNull(RESTAURANT_ID))
                .willReturn(Optional.of(existingDefault));
        given(restaurantTaxProfileRepository.save(any(RestaurantTaxProfile.class))).willAnswer(invocation -> {
            RestaurantTaxProfile taxProfile = invocation.getArgument(0);
            if (taxProfile.getId() == null) {
                taxProfile.setId(UUID.fromString("00000000-0000-0000-0000-000000000021"));
            }
            return taxProfile;
        });

        RestaurantTaxProfileResponse response = restaurantTaxProfileService.createTaxProfile(authentication, RESTAURANT_ID, request);

        assertThat(existingDefault.isDefault()).isFalse();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getIsDefault()).isTrue();
        verify(restaurantTaxProfileRepository).save(existingDefault);
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
}
