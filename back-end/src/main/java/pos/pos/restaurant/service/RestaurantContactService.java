package pos.pos.restaurant.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.restaurant.RestaurantContactNotFoundException;
import pos.pos.restaurant.dto.ContactResponse;
import pos.pos.restaurant.dto.UpsertContactRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.entity.RestaurantContact;
import pos.pos.restaurant.mapper.RestaurantContactMapper;
import pos.pos.restaurant.repository.RestaurantContactRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantContactService {

    private final RestaurantScopeService restaurantScopeService;
    private final RestaurantContactRepository restaurantContactRepository;
    private final RestaurantContactMapper restaurantContactMapper;

    public List<ContactResponse> getContacts(Authentication authentication, UUID restaurantId) {
        restaurantScopeService.requireAccessibleRestaurant(authentication, restaurantId);
        return restaurantContactRepository.findAllByRestaurantIdAndDeletedAtIsNullOrderByIsPrimaryDescCreatedAtAsc(restaurantId)
                .stream()
                .map(restaurantContactMapper::toResponse)
                .toList();
    }

    @Transactional
    public ContactResponse createContact(
            Authentication authentication,
            UUID restaurantId,
            UpsertContactRequest request
    ) {
        Restaurant restaurant = restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            clearExistingPrimary(restaurantId, null, actorId);
        }

        RestaurantContact contact = restaurantContactMapper.toNewEntity(restaurant, request, actorId);
        restaurantContactRepository.save(contact);
        return restaurantContactMapper.toResponse(contact);
    }

    @Transactional
    public ContactResponse updateContact(
            Authentication authentication,
            UUID restaurantId,
            UUID contactId,
            UpsertContactRequest request
    ) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        RestaurantContact contact = findExistingContact(restaurantId, contactId);
        if (Boolean.TRUE.equals(request.getIsPrimary())) {
            clearExistingPrimary(restaurantId, contactId, actorId);
        }

        restaurantContactMapper.updateEntity(contact, request, actorId);
        restaurantContactRepository.save(contact);
        return restaurantContactMapper.toResponse(contact);
    }

    @Transactional
    public ContactResponse makePrimary(Authentication authentication, UUID restaurantId, UUID contactId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        UUID actorId = restaurantScopeService.currentUserId(authentication);
        RestaurantContact contact = findExistingContact(restaurantId, contactId);
        clearExistingPrimary(restaurantId, contactId, actorId);
        restaurantContactMapper.updatePrimary(contact, true, actorId);
        restaurantContactRepository.save(contact);
        return restaurantContactMapper.toResponse(contact);
    }

    @Transactional
    public void deleteContact(Authentication authentication, UUID restaurantId, UUID contactId) {
        restaurantScopeService.requireManageableRestaurant(authentication, restaurantId);
        RestaurantContact contact = findExistingContact(restaurantId, contactId);
        restaurantContactMapper.markDeleted(
                contact,
                restaurantScopeService.currentUserId(authentication),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
        restaurantContactRepository.save(contact);
    }

    private RestaurantContact findExistingContact(UUID restaurantId, UUID contactId) {
        return restaurantContactRepository.findByIdAndRestaurantIdAndDeletedAtIsNull(contactId, restaurantId)
                .orElseThrow(RestaurantContactNotFoundException::new);
    }

    private void clearExistingPrimary(UUID restaurantId, UUID keepContactId, UUID actorId) {
        restaurantContactRepository.findByRestaurantIdAndIsPrimaryTrueAndDeletedAtIsNull(restaurantId)
                .filter(existing -> !existing.getId().equals(keepContactId))
                .ifPresent(existing -> {
                    restaurantContactMapper.updatePrimary(existing, false, actorId);
                    restaurantContactRepository.save(existing);
                });
    }
}
