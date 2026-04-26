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
import pos.pos.exception.restaurant.RestaurantContactNotFoundException;
import pos.pos.restaurant.dto.ContactResponse;
import pos.pos.restaurant.dto.UpsertContactRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.entity.RestaurantContact;
import pos.pos.restaurant.enums.ContactType;
import pos.pos.restaurant.mapper.RestaurantContactMapper;
import pos.pos.restaurant.repository.RestaurantContactRepository;
import pos.pos.restaurant.service.RestaurantContactService;
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
@DisplayName("RestaurantContactService")
class RestaurantContactServiceTest {

    private static final UUID ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID RESTAURANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final UUID CONTACT_ID = UUID.fromString("00000000-0000-0000-0000-000000000012");

    @Mock
    private RestaurantScopeService restaurantScopeService;

    @Mock
    private RestaurantContactRepository restaurantContactRepository;

    @Spy
    private RestaurantContactMapper restaurantContactMapper = new RestaurantContactMapper();

    @InjectMocks
    private RestaurantContactService restaurantContactService;

    @Test
    @DisplayName("createContact should call clearPrimary when new contact is primary")
    void shouldClearExistingPrimaryContact() {
        Authentication authentication = authentication();
        UpsertContactRequest request = UpsertContactRequest.builder()
                .contactType(ContactType.SUPPORT)
                .fullName("Support Lead")
                .email("support@pos.local")
                .phone("+355691234567")
                .isPrimary(true)
                .build();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantContactRepository.save(any(RestaurantContact.class))).willAnswer(invocation -> {
            RestaurantContact c = invocation.getArgument(0);
            if (c.getId() == null) {
                c.setId(CONTACT_ID);
                c.setCreatedAt(OffsetDateTime.parse("2026-04-23T10:00:00Z"));
                c.setUpdatedAt(c.getCreatedAt());
            }
            return c;
        });

        ContactResponse response = restaurantContactService.createContact(authentication, RESTAURANT_ID, request);

        assertThat(response.getId()).isEqualTo(CONTACT_ID);
        assertThat(response.getIsPrimary()).isTrue();
        verify(restaurantContactRepository).clearPrimary(RESTAURANT_ID, null, ACTOR_ID);
    }

    @Test
    @DisplayName("createContact should not call clearPrimary when new contact is not primary")
    void shouldNotClearPrimaryWhenNotPrimary() {
        Authentication authentication = authentication();
        UpsertContactRequest request = UpsertContactRequest.builder()
                .contactType(ContactType.GENERAL)
                .fullName("General Contact")
                .isPrimary(false)
                .build();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantContactRepository.save(any(RestaurantContact.class))).willAnswer(invocation -> {
            RestaurantContact c = invocation.getArgument(0);
            c.setId(CONTACT_ID);
            c.setCreatedAt(OffsetDateTime.parse("2026-04-23T10:00:00Z"));
            c.setUpdatedAt(c.getCreatedAt());
            return c;
        });

        ContactResponse response = restaurantContactService.createContact(authentication, RESTAURANT_ID, request);

        assertThat(response.getIsPrimary()).isFalse();
        verify(restaurantContactRepository, never()).clearPrimary(any(), any(), any());
    }

    @Test
    @DisplayName("makePrimary should call clearPrimary and set the target contact as primary")
    void shouldReplaceExistingPrimaryContact() {
        Authentication authentication = authentication();
        RestaurantContact target = contact(CONTACT_ID, false);

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantContactRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(CONTACT_ID, RESTAURANT_ID))
                .willReturn(Optional.of(target));

        ContactResponse response = restaurantContactService.makePrimary(authentication, RESTAURANT_ID, CONTACT_ID);

        assertThat(target.isPrimary()).isTrue();
        assertThat(response.getId()).isEqualTo(CONTACT_ID);
        verify(restaurantContactRepository).clearPrimary(RESTAURANT_ID, CONTACT_ID, ACTOR_ID);
        verify(restaurantContactRepository).save(target);
    }

    @Test
    @DisplayName("updateContact should call clearPrimary when updating to primary")
    void shouldUpdateContact() {
        Authentication authentication = authentication();
        RestaurantContact target = contact(CONTACT_ID, false);

        UpsertContactRequest request = UpsertContactRequest.builder()
                .contactType(ContactType.MANAGER)
                .fullName("Updated Manager")
                .email("mgr.updated@pos.local")
                .isPrimary(true)
                .build();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantContactRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(CONTACT_ID, RESTAURANT_ID))
                .willReturn(Optional.of(target));
        given(restaurantContactRepository.save(any(RestaurantContact.class))).willAnswer(i -> i.getArgument(0));

        ContactResponse response = restaurantContactService.updateContact(authentication, RESTAURANT_ID, CONTACT_ID, request);

        assertThat(target.isPrimary()).isTrue();
        assertThat(response.getId()).isEqualTo(CONTACT_ID);
        verify(restaurantContactRepository).clearPrimary(RESTAURANT_ID, CONTACT_ID, ACTOR_ID);
    }

    @Test
    @DisplayName("updateContact should reject a non-existent contact")
    void shouldRejectMissingContact() {
        Authentication authentication = authentication();
        UpsertContactRequest request = UpsertContactRequest.builder()
                .contactType(ContactType.GENERAL)
                .fullName("Any Name")
                .build();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantContactRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(CONTACT_ID, RESTAURANT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                restaurantContactService.updateContact(authentication, RESTAURANT_ID, CONTACT_ID, request))
                .isInstanceOf(RestaurantContactNotFoundException.class);
    }

    @Test
    @DisplayName("deleteContact should soft-delete an existing contact")
    void shouldSoftDeleteContact() {
        Authentication authentication = authentication();
        RestaurantContact target = contact(CONTACT_ID, false);

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantContactRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(CONTACT_ID, RESTAURANT_ID))
                .willReturn(Optional.of(target));
        given(restaurantContactRepository.save(target)).willReturn(target);

        restaurantContactService.deleteContact(authentication, RESTAURANT_ID, CONTACT_ID);

        assertThat(target.getDeletedAt()).isNotNull();
        verify(restaurantContactRepository).save(target);
    }

    @Test
    @DisplayName("deleteContact should reject a non-existent contact")
    void shouldRejectMissingContactOnDelete() {
        Authentication authentication = authentication();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(restaurantContactRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(CONTACT_ID, RESTAURANT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                restaurantContactService.deleteContact(authentication, RESTAURANT_ID, CONTACT_ID))
                .isInstanceOf(RestaurantContactNotFoundException.class);
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

    private RestaurantContact contact(UUID contactId, boolean primary) {
        RestaurantContact contact = new RestaurantContact();
        contact.setId(contactId);
        contact.setRestaurant(restaurant());
        contact.setContactType(ContactType.SUPPORT);
        contact.setFullName("Support Lead");
        contact.setEmail("support@pos.local");
        contact.setPhone("+355691234567");
        contact.setPrimary(primary);
        contact.setCreatedAt(OffsetDateTime.parse("2026-04-23T10:00:00Z"));
        contact.setUpdatedAt(contact.getCreatedAt());
        return contact;
    }
}
