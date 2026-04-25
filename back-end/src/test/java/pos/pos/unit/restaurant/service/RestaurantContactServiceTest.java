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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
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
    @DisplayName("createContact should clear an existing primary restaurant contact")
    void shouldClearExistingPrimaryContact() {
        Authentication authentication = authentication();
        RestaurantContact existingPrimary = contact(UUID.fromString("00000000-0000-0000-0000-000000000020"), true);
        UpsertContactRequest request = UpsertContactRequest.builder()
                .contactType(ContactType.SUPPORT)
                .fullName("Support Lead")
                .email("support@pos.local")
                .phone("+355691234567")
                .isPrimary(true)
                .build();

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantContactRepository.findByRestaurantIdAndIsPrimaryTrueAndDeletedAtIsNull(RESTAURANT_ID))
                .willReturn(Optional.of(existingPrimary));
        given(restaurantContactRepository.save(any(RestaurantContact.class))).willAnswer(invocation -> {
            RestaurantContact contact = invocation.getArgument(0);
            if (contact.getId() == null) {
                contact.setId(CONTACT_ID);
                contact.setCreatedAt(OffsetDateTime.parse("2026-04-23T10:00:00Z"));
                contact.setUpdatedAt(contact.getCreatedAt());
            }
            return contact;
        });

        ContactResponse response = restaurantContactService.createContact(authentication, RESTAURANT_ID, request);

        assertThat(existingPrimary.isPrimary()).isFalse();
        assertThat(response.getId()).isEqualTo(CONTACT_ID);
        assertThat(response.getIsPrimary()).isTrue();
        verify(restaurantContactRepository).save(existingPrimary);
    }

    @Test
    @DisplayName("makePrimary should replace the existing primary restaurant contact")
    void shouldReplaceExistingPrimaryContact() {
        Authentication authentication = authentication();
        RestaurantContact existingPrimary = contact(UUID.fromString("00000000-0000-0000-0000-000000000020"), true);
        RestaurantContact target = contact(CONTACT_ID, false);

        given(restaurantScopeService.requireManageableRestaurant(authentication, RESTAURANT_ID)).willReturn(restaurant());
        given(restaurantScopeService.currentUserId(authentication)).willReturn(ACTOR_ID);
        given(restaurantContactRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(CONTACT_ID, RESTAURANT_ID))
                .willReturn(Optional.of(target));
        given(restaurantContactRepository.findByRestaurantIdAndIsPrimaryTrueAndDeletedAtIsNull(RESTAURANT_ID))
                .willReturn(Optional.of(existingPrimary));

        ContactResponse response = restaurantContactService.makePrimary(authentication, RESTAURANT_ID, CONTACT_ID);

        assertThat(existingPrimary.isPrimary()).isFalse();
        assertThat(target.isPrimary()).isTrue();
        assertThat(response.getId()).isEqualTo(CONTACT_ID);
        verify(restaurantContactRepository).save(existingPrimary);
        verify(restaurantContactRepository).save(target);
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
