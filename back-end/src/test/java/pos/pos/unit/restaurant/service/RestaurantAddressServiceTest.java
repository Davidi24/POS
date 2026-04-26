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
import pos.pos.exception.restaurant.RestaurantAddressNotFoundException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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
    @DisplayName("createAddress should call clearPrimary when new address is primary")
    void shouldClearExistingPrimary() {
        Authentication authentication = authentication();
        Restaurant restaurant = restaurant();

        UpsertAddressRequest request = UpsertAddressRequest.builder()
                .addressType(AddressType.PHYSICAL)
                .country("Albania")
                .city("Tirana")
                .streetLine1("Main Street")
                .isPrimary(true)
                .build();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant);
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantAddressRepository.save(any(RestaurantAddress.class))).willAnswer(invocation -> {
            RestaurantAddress addr = invocation.getArgument(0);
            if (addr.getId() == null) {
                addr.setId(UUID.fromString("00000000-0000-0000-0000-000000000021"));
                addr.setCreatedAt(OffsetDateTime.parse("2026-04-23T10:00:00Z"));
                addr.setUpdatedAt(addr.getCreatedAt());
            }
            return addr;
        });

        AddressResponse response = restaurantAddressService.createAddress(authentication, RESTAURANT_ID, request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getIsPrimary()).isTrue();
        verify(restaurantAddressRepository).clearPrimary(RESTAURANT_ID, null, ACTOR_ID);
    }

    @Test
    @DisplayName("createAddress should not call clearPrimary when new address is not primary")
    void shouldNotClearPrimaryWhenNewAddressIsNotPrimary() {
        Authentication authentication = authentication();
        UpsertAddressRequest request = UpsertAddressRequest.builder()
                .addressType(AddressType.BILLING)
                .country("Albania")
                .city("Durres")
                .streetLine1("Billing Street")
                .isPrimary(false)
                .build();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantAddressRepository.save(any(RestaurantAddress.class))).willAnswer(invocation -> {
            RestaurantAddress addr = invocation.getArgument(0);
            addr.setId(UUID.fromString("00000000-0000-0000-0000-000000000021"));
            addr.setCreatedAt(OffsetDateTime.parse("2026-04-23T10:00:00Z"));
            addr.setUpdatedAt(addr.getCreatedAt());
            return addr;
        });

        AddressResponse response = restaurantAddressService.createAddress(authentication, RESTAURANT_ID, request);

        assertThat(response.getIsPrimary()).isFalse();
        verify(restaurantAddressRepository, never()).clearPrimary(any(), any(), any());
    }

    @Test
    @DisplayName("makePrimary should call clearPrimary and set the target address as primary")
    void shouldReplaceExistingPrimaryAddress() {
        Authentication authentication = authentication();
        RestaurantAddress target = address(ADDRESS_ID, false);

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantAddressRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(ADDRESS_ID, RESTAURANT_ID))
                .willReturn(Optional.of(target));

        AddressResponse response = restaurantAddressService.makePrimary(authentication, RESTAURANT_ID, ADDRESS_ID);

        assertThat(target.isPrimary()).isTrue();
        assertThat(response.getId()).isEqualTo(ADDRESS_ID);
        verify(restaurantAddressRepository).clearPrimary(RESTAURANT_ID, ADDRESS_ID, ACTOR_ID);
        verify(restaurantAddressRepository).save(target);
    }

    @Test
    @DisplayName("updateAddress should call clearPrimary when updating to primary")
    void shouldUpdateAddressAndClearPrimary() {
        Authentication authentication = authentication();
        RestaurantAddress target = address(ADDRESS_ID, false);

        UpsertAddressRequest request = UpsertAddressRequest.builder()
                .addressType(AddressType.BILLING)
                .country("Albania")
                .city("Durres")
                .streetLine1("Billing Street Updated")
                .isPrimary(true)
                .build();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantAddressRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(ADDRESS_ID, RESTAURANT_ID))
                .willReturn(Optional.of(target));
        given(restaurantAddressRepository.save(any(RestaurantAddress.class))).willAnswer(i -> i.getArgument(0));

        AddressResponse response = restaurantAddressService.updateAddress(authentication, RESTAURANT_ID, ADDRESS_ID, request);

        assertThat(target.isPrimary()).isTrue();
        assertThat(response.getId()).isEqualTo(ADDRESS_ID);
        verify(restaurantAddressRepository).clearPrimary(RESTAURANT_ID, ADDRESS_ID, ACTOR_ID);
        verify(restaurantAddressRepository).save(target);
    }

    @Test
    @DisplayName("updateAddress should reject a non-existent address")
    void shouldRejectMissingAddress() {
        Authentication authentication = authentication();
        UpsertAddressRequest request = UpsertAddressRequest.builder()
                .addressType(AddressType.PHYSICAL)
                .country("Albania")
                .city("Tirana")
                .streetLine1("Any Street")
                .build();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantAddressRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(ADDRESS_ID, RESTAURANT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                restaurantAddressService.updateAddress(authentication, RESTAURANT_ID, ADDRESS_ID, request))
                .isInstanceOf(RestaurantAddressNotFoundException.class);
    }

    @Test
    @DisplayName("deleteAddress should soft-delete an existing address")
    void shouldSoftDeleteAddress() {
        Authentication authentication = authentication();
        RestaurantAddress target = address(ADDRESS_ID, false);

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantAddressRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(ADDRESS_ID, RESTAURANT_ID))
                .willReturn(Optional.of(target));
        given(restaurantAddressRepository.save(target)).willReturn(target);

        restaurantAddressService.deleteAddress(authentication, RESTAURANT_ID, ADDRESS_ID);

        assertThat(target.getDeletedAt()).isNotNull();
        verify(restaurantAddressRepository).save(target);
    }

    @Test
    @DisplayName("deleteAddress should reject a non-existent address")
    void shouldRejectMissingAddressOnDelete() {
        Authentication authentication = authentication();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(restaurantAddressRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(ADDRESS_ID, RESTAURANT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                restaurantAddressService.deleteAddress(authentication, RESTAURANT_ID, ADDRESS_ID))
                .isInstanceOf(RestaurantAddressNotFoundException.class);
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
