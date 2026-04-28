package pos.pos.restaurant.mapper;

import org.springframework.stereotype.Component;
import pos.pos.restaurant.dto.ContactResponse;
import pos.pos.restaurant.dto.UpsertContactRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.entity.RestaurantContact;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class RestaurantContactMapper {

    public ContactResponse toResponse(RestaurantContact contact) {
        if (contact == null) {
            return null;
        }

        return ContactResponse.builder()
                .id(contact.getId())
                .contactType(contact.getContactType())
                .fullName(contact.getFullName())
                .email(contact.getEmail())
                .phone(contact.getPhone())
                .isPrimary(contact.isPrimary())
                .jobTitle(contact.getJobTitle())
                .createdAt(contact.getCreatedAt())
                .updatedAt(contact.getUpdatedAt())
                .build();
    }

    public RestaurantContact toNewEntity(Restaurant restaurant, UpsertContactRequest request, UUID actorId) {
        RestaurantContact contact = new RestaurantContact();
        contact.setRestaurant(restaurant);
        applyFields(contact, request);
        contact.setPrimary(Boolean.TRUE.equals(request.getIsPrimary()));
        contact.setCreatedBy(actorId);
        contact.setUpdatedBy(actorId);
        return contact;
    }

    public void updateEntity(RestaurantContact contact, UpsertContactRequest request, UUID actorId) {
        applyFields(contact, request);
        contact.setPrimary(Boolean.TRUE.equals(request.getIsPrimary()));
        contact.setUpdatedBy(actorId);
    }

    public void updatePrimary(RestaurantContact contact, boolean isPrimary, UUID actorId) {
        contact.setPrimary(isPrimary);
        contact.setUpdatedBy(actorId);
    }

    public void markDeleted(RestaurantContact contact, UUID actorId, OffsetDateTime deletedAt) {
        contact.setDeletedAt(deletedAt);
        contact.setUpdatedBy(actorId);
    }

    private void applyFields(RestaurantContact contact, UpsertContactRequest request) {
        contact.setContactType(request.getContactType());
        contact.setFullName(request.getFullName());
        contact.setEmail(request.getEmail());
        contact.setPhone(request.getPhone());
        contact.setJobTitle(request.getJobTitle());
    }
}
