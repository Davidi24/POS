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
import pos.pos.restaurant.dto.AddressResponse;
import pos.pos.restaurant.dto.UpsertAddressRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.entity.RestaurantAddress;
import pos.pos.restaurant.enums.AddressType;
import pos.pos.restaurant.mapper.RestaurantAddressMapper;
import pos.pos.restaurant.repository.RestaurantAddressRepository;
import pos.pos.restaurant.service.RestaurantAddressService;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.security.principal.AuthenticatedUser;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestaurantAddressService")
class RestaurantAddressServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID ADDRESS_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");

    @Mock
    private RestaurantScopeService restaurantScopeService;

    @Mock
    private RestaurantAddressRepository restaurantAddressRepository;

    @Spy
    private RestaurantAddressMapper restaurantAddressMapper = new RestaurantAddressMapper();

    @InjectMocks
    private RestaurantAddressService restaurantAddressService;

    @Test
    @DisplayName("createAddress should clear an existing primary address")
    void shouldClearExistingPrimary() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();
        RestaurantAddress existingPrimary = new RestaurantAddress();
        existingPrimary.setId(UUID.fromString("00000000-0000-0000-0000-000000000020"));
        existingPrimary.setPrimary(true);

        UpsertAddressRequest request = UpsertAddressRequest.builder()
                .addressType(AddressType.PHYSICAL)
                .country("Albania")
                .city("Tirana")
                .streetLine1("Main Street")
                .isPrimary(true)
                .build();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant);
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantAddressRepository.findByRestaurantIdAndIsPrimaryTrueAndDeletedAtIsNull(RESTAURANT_ID))
                .willReturn(Optional.of(existingPrimary));
        given(restaurantAddressRepository.save(any(RestaurantAddress.class))).willAnswer(invocation -> {
            RestaurantAddress address = invocation.getArgument(0);
            if (address.getId() == null) {
                address.setId(UUID.fromString("00000000-0000-0000-0000-000000000021"));
                address.setCreatedAt(OffsetDateTime.parse("2026-04-23T10:00:00Z"));
                address.setUpdatedAt(address.getCreatedAt());
            }
            return address;
        });

        AddressResponse response = restaurantAddressService.createAddress(authentication, RESTAURANT_ID, request);

        assertThat(existingPrimary.isPrimary()).isFalse();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getIsPrimary()).isTrue();
        verify(restaurantAddressRepository).save(existingPrimary);
    }

    @Test
    @DisplayName("makePrimary should replace the existing primary restaurant address")
    void shouldReplaceExistingPrimaryAddress() {
        Authentication authentication = authentication();
        RestaurantAddress existingPrimary = address(UUID.fromString("00000000-0000-0000-0000-000000000020"), true);
        RestaurantAddress target = address(ADDRESS_ID, false);

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantAddressRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(ADDRESS_ID, RESTAURANT_ID))
                .willReturn(Optional.of(target));
        given(restaurantAddressRepository.findByRestaurantIdAndIsPrimaryTrueAndDeletedAtIsNull(RESTAURANT_ID))
                .willReturn(Optional.of(existingPrimary));

        AddressResponse response = restaurantAddressService.makePrimary(authentication, RESTAURANT_ID, ADDRESS_ID);

        assertThat(existingPrimary.isPrimary()).isFalse();
        assertThat(target.isPrimary()).isTrue();
        assertThat(response.getId()).isEqualTo(ADDRESS_ID);
        verify(restaurantAddressRepository).save(existingPrimary);
        verify(restaurantAddressRepository).save(target);
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

    private RestaurantAddress address(UUID addressId, boolean primary) {
        RestaurantAddress address = new RestaurantAddress();
        address.setId(addressId);
        address.setRestaurant(restaurant());
        address.setAddressType(AddressType.PHYSICAL);
        address.setCountry("Albania");
        address.setCity("Tirana");
        address.setStreetLine1("Main Street");
        address.setPrimary(primary);
        address.setCreatedAt(OffsetDateTime.parse("2026-04-23T10:00:00Z"));
        address.setUpdatedAt(address.getCreatedAt());
        return address;
    }
}
