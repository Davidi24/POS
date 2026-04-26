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
import pos.pos.exception.restaurant.BranchContactNotFoundException;
import pos.pos.restaurant.dto.ContactResponse;
import pos.pos.restaurant.dto.UpsertContactRequest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
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
    @DisplayName("createContact should call clearPrimary when new contact is primary")
    void shouldClearExistingPrimaryContact() {
        Authentication authentication = authentication();
        UpsertContactRequest request = UpsertContactRequest.builder()
                .contactType(ContactType.SUPPORT)
                .fullName("Support Lead")
                .email("support@branch.local")
                .phone("+355691234567")
                .isPrimary(true)
                .build();

        given(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).willReturn(branch());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(branchContactRepository.save(any(BranchContact.class))).willAnswer(invocation -> {
            BranchContact c = invocation.getArgument(0);
            if (c.getId() == null) {
                c.setId(CONTACT_ID);
                c.setCreatedAt(OffsetDateTime.parse("2026-04-23T10:00:00Z"));
                c.setUpdatedAt(c.getCreatedAt());
            }
            return c;
        });

        ContactResponse response = branchContactService.createContact(authentication, RESTAURANT_ID, BRANCH_ID, request);

        assertThat(response.getId()).isEqualTo(CONTACT_ID);
        assertThat(response.getIsPrimary()).isTrue();
        verify(branchContactRepository).clearPrimary(BRANCH_ID, null, ACTOR_ID);
    }

    @Test
    @DisplayName("createContact should not call clearPrimary when new contact is not primary")
    void shouldNotClearPrimaryWhenNewContactIsNotPrimary() {
        Authentication authentication = authentication();
        UpsertContactRequest request = UpsertContactRequest.builder()
                .contactType(ContactType.GENERAL)
                .fullName("General Contact")
                .isPrimary(false)
                .build();

        given(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).willReturn(branch());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(branchContactRepository.save(any(BranchContact.class))).willAnswer(invocation -> {
            BranchContact c = invocation.getArgument(0);
            c.setId(CONTACT_ID);
            c.setCreatedAt(OffsetDateTime.parse("2026-04-23T10:00:00Z"));
            c.setUpdatedAt(c.getCreatedAt());
            return c;
        });

        ContactResponse response = branchContactService.createContact(authentication, RESTAURANT_ID, BRANCH_ID, request);

        assertThat(response.getIsPrimary()).isFalse();
        verify(branchContactRepository, never()).clearPrimary(any(), any(), any());
    }

    @Test
    @DisplayName("makePrimary should call clearPrimary and set the target contact as primary")
    void shouldReplaceExistingPrimaryContact() {
        Authentication authentication = authentication();
        BranchContact target = contact(CONTACT_ID, false);

        given(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).willReturn(branch());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(branchContactRepository.findByIdAndBranchIdAndDeletedAtIsNull(CONTACT_ID, BRANCH_ID))
                .willReturn(Optional.of(target));

        ContactResponse response = branchContactService.makePrimary(authentication, RESTAURANT_ID, BRANCH_ID, CONTACT_ID);

        assertThat(target.isPrimary()).isTrue();
        assertThat(response.getId()).isEqualTo(CONTACT_ID);
        verify(branchContactRepository).clearPrimary(BRANCH_ID, CONTACT_ID, ACTOR_ID);
        verify(branchContactRepository).save(target);
    }

    @Test
    @DisplayName("updateContact should call clearPrimary and update fields")
    void shouldUpdateContact() {
        Authentication authentication = authentication();
        BranchContact target = contact(CONTACT_ID, false);

        UpsertContactRequest request = UpsertContactRequest.builder()
                .contactType(ContactType.MANAGER)
                .fullName("Updated Manager")
                .email("manager.updated@branch.local")
                .isPrimary(true)
                .build();

        given(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).willReturn(branch());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(branchContactRepository.findByIdAndBranchIdAndDeletedAtIsNull(CONTACT_ID, BRANCH_ID))
                .willReturn(Optional.of(target));
        given(branchContactRepository.save(any(BranchContact.class))).willAnswer(i -> i.getArgument(0));

        ContactResponse response = branchContactService.updateContact(
                authentication, RESTAURANT_ID, BRANCH_ID, CONTACT_ID, request);

        assertThat(target.isPrimary()).isTrue();
        assertThat(response.getId()).isEqualTo(CONTACT_ID);
        verify(branchContactRepository).clearPrimary(BRANCH_ID, CONTACT_ID, ACTOR_ID);
    }

    @Test
    @DisplayName("updateContact should reject a non-existent contact")
    void shouldRejectMissingContact() {
        Authentication authentication = authentication();
        UpsertContactRequest request = UpsertContactRequest.builder()
                .contactType(ContactType.GENERAL)
                .fullName("Any Name")
                .build();

        given(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).willReturn(branch());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(branchContactRepository.findByIdAndBranchIdAndDeletedAtIsNull(CONTACT_ID, BRANCH_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                branchContactService.updateContact(authentication, RESTAURANT_ID, BRANCH_ID, CONTACT_ID, request))
                .isInstanceOf(BranchContactNotFoundException.class);
    }

    @Test
    @DisplayName("deleteContact should soft-delete an existing contact")
    void shouldSoftDeleteContact() {
        Authentication authentication = authentication();
        BranchContact target = contact(CONTACT_ID, false);

        given(restaurantScopeService.requireManageableBranch(authentication, RESTAURANT_ID, BRANCH_ID)).willReturn(branch());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(branchContactRepository.findByIdAndBranchIdAndDeletedAtIsNull(CONTACT_ID, BRANCH_ID))
                .willReturn(Optional.of(target));
        given(branchContactRepository.save(target)).willReturn(target);

        branchContactService.deleteContact(authentication, RESTAURANT_ID, BRANCH_ID, CONTACT_ID);

        assertThat(target.getDeletedAt()).isNotNull();
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
