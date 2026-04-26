package pos.pos.restaurant.mapper;

import org.springframework.stereotype.Component;
import pos.pos.restaurant.dto.CreateRestaurantRequest;
import pos.pos.restaurant.dto.RestaurantResponse;
import pos.pos.restaurant.dto.RestaurantRegistrationRequest;
import pos.pos.restaurant.dto.UpdateRestaurantRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.enums.RestaurantStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class RestaurantMapper {

    public RestaurantResponse toResponse(Restaurant restaurant) {
        if (restaurant == null) {
            return null;
        }

        return RestaurantResponse.builder()
                .id(restaurant.getId())
                .name(restaurant.getName())
                .legalName(restaurant.getLegalName())
                .code(restaurant.getCode())
                .slug(restaurant.getSlug())
                .description(restaurant.getDescription())
                .email(restaurant.getEmail())
                .phone(restaurant.getPhone())
                .website(restaurant.getWebsite())
                .currency(restaurant.getCurrency())
                .timezone(restaurant.getTimezone())
                .isActive(restaurant.isActive())
                .status(restaurant.getStatus())
                .ownerUserId(restaurant.getOwnerId())
                .rejectionReason(restaurant.getRejectionReason())
                .createdAt(restaurant.getCreatedAt())
                .updatedAt(restaurant.getUpdatedAt())
                .build();
    }

    public Restaurant toNewEntity(
            CreateRestaurantRequest request,
            String normalizedCode,
            String normalizedSlug,
            boolean isActive,
            RestaurantStatus status,
            UUID actorId
    ) {
        Restaurant restaurant = new Restaurant();
        applyBaseFields(
                restaurant,
                request.getName(),
                request.getLegalName(),
                normalizedCode,
                normalizedSlug,
                request.getDescription(),
                request.getEmail(),
                request.getPhone(),
                request.getWebsite(),
                request.getCurrency(),
                request.getTimezone()
        );
        restaurant.setActive(isActive);
        restaurant.setStatus(status);
        restaurant.setCreatedBy(actorId);
        restaurant.setUpdatedBy(actorId);
        return restaurant;
    }

    public Restaurant toPendingRegistrationEntity(
            RestaurantRegistrationRequest request,
            String normalizedCode,
            String normalizedSlug
    ) {
        Restaurant restaurant = new Restaurant();
        applyBaseFields(
                restaurant,
                request.getName(),
                request.getLegalName(),
                normalizedCode,
                normalizedSlug,
                request.getDescription(),
                request.getEmail(),
                request.getPhone(),
                request.getWebsite(),
                request.getCurrency(),
                request.getTimezone()
        );
        restaurant.setActive(false);
        restaurant.setStatus(RestaurantStatus.PENDING);
        restaurant.setPendingOwnerEmail(request.getOwner().getEmail());
        restaurant.setPendingOwnerUsername(request.getOwner().getUsername());
        restaurant.setPendingOwnerFirstName(request.getOwner().getFirstName());
        restaurant.setPendingOwnerLastName(request.getOwner().getLastName());
        restaurant.setPendingOwnerPhone(request.getOwner().getPhone());
        restaurant.setPendingOwnerClientTarget(request.getOwner().getClientTarget());
        return restaurant;
    }

    public void updateEntity(
            Restaurant restaurant,
            UpdateRestaurantRequest request,
            String normalizedCode,
            String normalizedSlug,
            UUID ownerUserId,
            UUID actorId
    ) {
        applyBaseFields(
                restaurant,
                request.getName(),
                request.getLegalName(),
                normalizedCode,
                normalizedSlug,
                request.getDescription(),
                request.getEmail(),
                request.getPhone(),
                request.getWebsite(),
                request.getCurrency(),
                request.getTimezone()
        );
        restaurant.setOwnerId(ownerUserId);
        restaurant.setActive(request.getIsActive());
        restaurant.setStatus(request.getStatus());
        restaurant.setUpdatedBy(actorId);
    }

    public void updateStatus(Restaurant restaurant, boolean isActive, RestaurantStatus status, UUID actorId) {
        restaurant.setActive(isActive);
        restaurant.setStatus(status);
        restaurant.setUpdatedBy(actorId);
    }

    public void markDeleted(Restaurant restaurant, UUID actorId, OffsetDateTime deletedAt) {
        updateStatus(restaurant, false, RestaurantStatus.ARCHIVED, actorId);
        restaurant.setDeletedAt(deletedAt);
    }

    public void markRegistrationApproved(Restaurant restaurant, UUID ownerUserId, UUID actorId) {
        restaurant.setOwnerId(ownerUserId);
        restaurant.setActive(true);
        restaurant.setStatus(RestaurantStatus.ACTIVE);
        clearPendingOwnerSnapshot(restaurant);
        restaurant.setUpdatedBy(actorId);
    }

    public void markRegistrationRejected(Restaurant restaurant, String rejectionReason, UUID actorId) {
        restaurant.setActive(false);
        restaurant.setStatus(RestaurantStatus.REJECTED);
        restaurant.setRejectionReason(rejectionReason);
        restaurant.setUpdatedBy(actorId);
    }

    private void applyBaseFields(
            Restaurant restaurant,
            String name,
            String legalName,
            String code,
            String slug,
            String description,
            String email,
            String phone,
            String website,
            String currency,
            String timezone
    ) {
        restaurant.setName(name);
        restaurant.setLegalName(legalName);
        restaurant.setCode(code);
        restaurant.setSlug(slug);
        restaurant.setDescription(description);
        restaurant.setEmail(email);
        restaurant.setPhone(phone);
        restaurant.setWebsite(website);
        restaurant.setCurrency(currency);
        restaurant.setTimezone(timezone);
    }

    private void clearPendingOwnerSnapshot(Restaurant restaurant) {
        restaurant.setPendingOwnerEmail(null);
        restaurant.setPendingOwnerUsername(null);
        restaurant.setPendingOwnerFirstName(null);
        restaurant.setPendingOwnerLastName(null);
        restaurant.setPendingOwnerPhone(null);
        restaurant.setPendingOwnerClientTarget(null);
    }
}
