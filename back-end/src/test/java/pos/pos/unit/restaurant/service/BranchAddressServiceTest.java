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
import pos.pos.exception.restaurant.BranchAddressNotFoundException;
import pos.pos.restaurant.dto.AddressResponse;
import pos.pos.restaurant.dto.UpsertAddressRequest;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.BranchAddress;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.AddressType;
import pos.pos.restaurant.mapper.BranchAddressMapper;
import pos.pos.restaurant.repository.BranchAddressRepository;
import pos.pos.restaurant.service.BranchAddressService;
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
@DisplayName("BranchAddressService")
class BranchAddressServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID ADDRESS_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");

    @Mock
    private RestaurantScopeService restaurantScopeService;

    @Mock
    private BranchAddressRepository branchAddressRepository;

    @Spy
    private BranchAddressMapper branchAddressMapper = new BranchAddressMapper();

    @InjectMocks
    private BranchAddressService branchAddressService;

    @Test
    @DisplayName("createAddress should call clearPrimary when new address is primary")
    void shouldClearExistingPrimary() {
        Authentication authentication = authentication();
        Branch branch = branch();

        UpsertAddressRequest request = UpsertAddressRequest.builder()
                .addressType(AddressType.PHYSICAL)
                .country("Albania")
                .city("Tirana")
                .streetLine1("Main Street")
                .isPrimary(true)
                .build();

        given(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).willReturn(branch);
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(branchAddressRepository.save(any(BranchAddress.class))).willAnswer(invocation -> {
            BranchAddress address = invocation.getArgument(0);
            if (address.getId() == null) {
                address.setId(UUID.fromString("00000000-0000-0000-0000-000000000021"));
                address.setCreatedAt(OffsetDateTime.parse("2026-04-23T10:00:00Z"));
                address.setUpdatedAt(address.getCreatedAt());
            }
            return address;
        });

        AddressResponse response = branchAddressService.createAddress(authentication, RESTAURANT_ID, BRANCH_ID, request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getIsPrimary()).isTrue();
        verify(branchAddressRepository).clearPrimary(BRANCH_ID, null, ACTOR_ID);
    }

    @Test
    @DisplayName("createAddress should not call clearPrimary when new address is not primary")
    void shouldNotClearPrimaryWhenNewAddressIsNotPrimary() {
        Authentication authentication = authentication();
        Branch branch = branch();
        UpsertAddressRequest request = UpsertAddressRequest.builder()
                .addressType(AddressType.BILLING)
                .country("Albania")
                .city("Durres")
                .streetLine1("Billing Street")
                .isPrimary(false)
                .build();

        given(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).willReturn(branch);
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(branchAddressRepository.save(any(BranchAddress.class))).willAnswer(invocation -> {
            BranchAddress address = invocation.getArgument(0);
            address.setId(UUID.fromString("00000000-0000-0000-0000-000000000021"));
            address.setCreatedAt(OffsetDateTime.parse("2026-04-23T10:00:00Z"));
            address.setUpdatedAt(address.getCreatedAt());
            return address;
        });

        AddressResponse response = branchAddressService.createAddress(authentication, RESTAURANT_ID, BRANCH_ID, request);

        assertThat(response.getIsPrimary()).isFalse();
        verify(branchAddressRepository, never()).clearPrimary(any(), any(), any());
    }

    @Test
    @DisplayName("makePrimary should call clearPrimary and set the target address as primary")
    void shouldReplaceExistingPrimaryAddress() {
        Authentication authentication = authentication();
        BranchAddress target = address(ADDRESS_ID, false);

        given(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).willReturn(branch());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(branchAddressRepository.findByIdAndBranchIdAndDeletedAtIsNull(ADDRESS_ID, BRANCH_ID))
                .willReturn(Optional.of(target));

        AddressResponse response = branchAddressService.makePrimary(authentication, RESTAURANT_ID, BRANCH_ID, ADDRESS_ID);

        assertThat(target.isPrimary()).isTrue();
        assertThat(response.getId()).isEqualTo(ADDRESS_ID);
        verify(branchAddressRepository).clearPrimary(BRANCH_ID, ADDRESS_ID, ACTOR_ID);
        verify(branchAddressRepository).save(target);
    }

    @Test
    @DisplayName("updateAddress should call clearPrimary and update fields")
    void shouldUpdateAddressAndClearPrimary() {
        Authentication authentication = authentication();
        BranchAddress target = address(ADDRESS_ID, false);

        UpsertAddressRequest request = UpsertAddressRequest.builder()
                .addressType(AddressType.SHIPPING)
                .country("Albania")
                .city("Vlore")
                .streetLine1("Updated Street")
                .isPrimary(true)
                .build();

        given(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).willReturn(branch());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(branchAddressRepository.findByIdAndBranchIdAndDeletedAtIsNull(ADDRESS_ID, BRANCH_ID))
                .willReturn(Optional.of(target));
        given(branchAddressRepository.save(any(BranchAddress.class))).willAnswer(i -> i.getArgument(0));

        AddressResponse response = branchAddressService.updateAddress(
                authentication, RESTAURANT_ID, BRANCH_ID, ADDRESS_ID, request);

        assertThat(target.isPrimary()).isTrue();
        assertThat(response.getId()).isEqualTo(ADDRESS_ID);
        verify(branchAddressRepository).clearPrimary(BRANCH_ID, ADDRESS_ID, ACTOR_ID);
        verify(branchAddressRepository).save(target);
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

        given(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).willReturn(branch());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(branchAddressRepository.findByIdAndBranchIdAndDeletedAtIsNull(ADDRESS_ID, BRANCH_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                branchAddressService.updateAddress(authentication, RESTAURANT_ID, BRANCH_ID, ADDRESS_ID, request))
                .isInstanceOf(BranchAddressNotFoundException.class);
    }

    @Test
    @DisplayName("deleteAddress should soft-delete an existing address")
    void shouldSoftDeleteAddress() {
        Authentication authentication = authentication();
        BranchAddress target = address(ADDRESS_ID, false);

        given(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).willReturn(branch());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(branchAddressRepository.findByIdAndBranchIdAndDeletedAtIsNull(ADDRESS_ID, BRANCH_ID))
                .willReturn(Optional.of(target));
        given(branchAddressRepository.save(target)).willReturn(target);

        branchAddressService.deleteAddress(authentication, RESTAURANT_ID, BRANCH_ID, ADDRESS_ID);

        assertThat(target.getDeletedAt()).isNotNull();
        verify(branchAddressRepository).save(target);
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

    private Branch branch() {
        Branch branch = new Branch();
        branch.setId(BRANCH_ID);
        Restaurant restaurant = new Restaurant();
        restaurant.setId(RESTAURANT_ID);
        branch.setRestaurant(restaurant);
        branch.setName("Downtown");
        return branch;
    }

    private BranchAddress address(UUID addressId, boolean primary) {
        BranchAddress address = new BranchAddress();
        address.setId(addressId);
        address.setBranch(branch());
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
