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
import pos.pos.exception.restaurant.RestaurantBrandingNotFoundException;
import pos.pos.restaurant.dto.RestaurantBrandingResponse;
import pos.pos.restaurant.dto.UpsertRestaurantBrandingRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.entity.RestaurantBranding;
import pos.pos.restaurant.mapper.RestaurantBrandingMapper;
import pos.pos.restaurant.repository.RestaurantBrandingRepository;
import pos.pos.restaurant.service.RestaurantBrandingService;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.security.principal.AuthenticatedUser;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestaurantBrandingService")
class RestaurantBrandingServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");

    @Mock
    private RestaurantScopeService restaurantScopeService;

    @Mock
    private RestaurantBrandingRepository restaurantBrandingRepository;

    @Spy
    private RestaurantBrandingMapper restaurantBrandingMapper = new RestaurantBrandingMapper();

    @InjectMocks
    private RestaurantBrandingService restaurantBrandingService;

    @Test
    @DisplayName("getBranding should throw when branding does not exist")
    void shouldRejectMissingBranding() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();

        given(restaurantScopeService.requireAccessibleRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant);
        given(restaurantBrandingRepository.findByRestaurantIdAndDeletedAtIsNull(RESTAURANT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> restaurantBrandingService.getBranding(authentication, RESTAURANT_ID))
                .isInstanceOf(RestaurantBrandingNotFoundException.class);
    }

    @Test
    @DisplayName("upsertBranding should create branding when missing")
    void shouldCreateBranding() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();
        UpsertRestaurantBrandingRequest request = UpsertRestaurantBrandingRequest.builder()
                .logoUrl("https://cdn.pos.local/logo.png")
                .primaryColor("#112233")
                .secondaryColor("#445566")
                .receiptHeader("Hello")
                .receiptFooter("Thanks")
                .build();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant);
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantBrandingRepository.findByRestaurantIdAndDeletedAtIsNull(RESTAURANT_ID)).willReturn(Optional.empty());
        given(restaurantBrandingRepository.save(any(RestaurantBranding.class))).willAnswer(invocation -> {
            RestaurantBranding branding = invocation.getArgument(0);
            branding.setId(UUID.fromString("00000000-0000-0000-0000-000000000011"));
            return branding;
        });

        RestaurantBrandingResponse response = restaurantBrandingService.upsertBranding(authentication, RESTAURANT_ID, request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getPrimaryColor()).isEqualTo("#112233");
        verify(restaurantBrandingRepository).save(any(RestaurantBranding.class));
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
