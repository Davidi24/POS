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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BranchAddressService")
class BranchAddressServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    @Mock
    private RestaurantScopeService restaurantScopeService;

    @Mock
    private BranchAddressRepository branchAddressRepository;

    @Spy
    private BranchAddressMapper branchAddressMapper = new BranchAddressMapper();

    @InjectMocks
    private BranchAddressService branchAddressService;

    @Test
    @DisplayName("createAddress should clear an existing primary branch address")
    void shouldClearExistingPrimary() {
        Authentication authentication = authentication();
        Branch branch = branch();
        BranchAddress existingPrimary = new BranchAddress();
        existingPrimary.setId(UUID.fromString("00000000-0000-0000-0000-000000000020"));
        existingPrimary.setPrimary(true);

        UpsertAddressRequest request = UpsertAddressRequest.builder()
                .addressType(AddressType.PHYSICAL)
                .country("Albania")
                .city("Tirana")
                .streetLine1("Main Street")
                .isPrimary(true)
                .build();

        given(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).willReturn(branch);
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(branchAddressRepository.findByBranchIdAndIsPrimaryTrueAndDeletedAtIsNull(BRANCH_ID))
                .willReturn(Optional.of(existingPrimary));
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

        assertThat(existingPrimary.isPrimary()).isFalse();
        assertThat(response.getId()).isNotNull();
        assertThat(response.getIsPrimary()).isTrue();
        verify(branchAddressRepository).save(existingPrimary);
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
}
