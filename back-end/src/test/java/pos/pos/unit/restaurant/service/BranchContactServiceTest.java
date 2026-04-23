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
import pos.pos.restaurant.dto.ContactResponse;
import pos.pos.restaurant.entity.Branch;
import pos.pos.restaurant.entity.BranchContact;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.ContactType;
import pos.pos.restaurant.mapper.BranchContactMapper;
import pos.pos.restaurant.repository.BranchContactRepository;
import pos.pos.restaurant.service.BranchContactService;
import pos.pos.restaurant.service.RestaurantScopeService;
import pos.pos.security.principal.AuthenticatedUser;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BranchContactService")
class BranchContactServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID BRANCH_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final UUID CONTACT_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");

    @Mock
    private RestaurantScopeService restaurantScopeService;

    @Mock
    private BranchContactRepository branchContactRepository;

    @Spy
    private BranchContactMapper branchContactMapper = new BranchContactMapper();

    @InjectMocks
    private BranchContactService branchContactService;

    @Test
    @DisplayName("makePrimary should replace the existing primary branch contact")
    void shouldReplaceExistingPrimaryContact() {
        Authentication authentication = authentication();
        BranchContact existingPrimary = contact(UUID.fromString("00000000-0000-0000-0000-000000000020"), true);
        BranchContact target = contact(CONTACT_ID, false);

        given(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).willReturn(branch());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(branchContactRepository.findByIdAndBranchIdAndDeletedAtIsNull(CONTACT_ID, BRANCH_ID))
                .willReturn(Optional.of(target));
        given(branchContactRepository.findByBranchIdAndIsPrimaryTrueAndDeletedAtIsNull(BRANCH_ID))
                .willReturn(Optional.of(existingPrimary));

        ContactResponse response = branchContactService.makePrimary(authentication, RESTAURANT_ID, BRANCH_ID, CONTACT_ID);

        assertThat(existingPrimary.isPrimary()).isFalse();
        assertThat(target.isPrimary()).isTrue();
        assertThat(response.getId()).isEqualTo(CONTACT_ID);
        verify(branchContactRepository).save(existingPrimary);
        verify(branchContactRepository).save(target);
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

    private BranchContact contact(UUID contactId, boolean primary) {
        BranchContact contact = new BranchContact();
        contact.setId(contactId);
        contact.setBranch(branch());
        contact.setContactType(ContactType.MANAGER);
        contact.setFullName("Manager Name");
        contact.setPrimary(primary);
        contact.setCreatedAt(OffsetDateTime.parse("2026-04-23T10:00:00Z"));
        contact.setUpdatedAt(contact.getCreatedAt());
        return contact;
    }
}
