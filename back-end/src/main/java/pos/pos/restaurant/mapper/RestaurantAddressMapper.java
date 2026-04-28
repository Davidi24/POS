package pos.pos.restaurant.mapper;

import org.springframework.stereotype.Component;
import pos.pos.restaurant.dto.AddressResponse;
import pos.pos.restaurant.dto.UpsertAddressRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.entity.RestaurantAddress;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class RestaurantAddressMapper {

    public AddressResponse toResponse(RestaurantAddress address) {
        if (address == null) {
            return null;
        }

        return AddressResponse.builder()
                .id(address.getId())
                .addressType(address.getAddressType())
                .country(address.getCountry())
                .city(address.getCity())
                .postalCode(address.getPostalCode())
                .streetLine1(address.getStreetLine1())
                .streetLine2(address.getStreetLine2())
                .isPrimary(address.isPrimary())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }

    public RestaurantAddress toNewEntity(Restaurant restaurant, UpsertAddressRequest request, UUID actorId) {
        RestaurantAddress address = new RestaurantAddress();
        address.setRestaurant(restaurant);
        applyFields(address, request);
        address.setPrimary(Boolean.TRUE.equals(request.getIsPrimary()));
        address.setCreatedBy(actorId);
        address.setUpdatedBy(actorId);
        return address;
    }

    public void updateEntity(RestaurantAddress address, UpsertAddressRequest request, UUID actorId) {
        applyFields(address, request);
        address.setPrimary(Boolean.TRUE.equals(request.getIsPrimary()));
        address.setUpdatedBy(actorId);
    }

    public void updatePrimary(RestaurantAddress address, boolean isPrimary, UUID actorId) {
        address.setPrimary(isPrimary);
        address.setUpdatedBy(actorId);
    }

    public void markDeleted(RestaurantAddress address, UUID actorId, OffsetDateTime deletedAt) {
        address.setDeletedAt(deletedAt);
        address.setUpdatedBy(actorId);
    }

    private void applyFields(RestaurantAddress address, UpsertAddressRequest request) {
        address.setAddressType(request.getAddressType());
        address.setCountry(request.getCountry());
        address.setCity(request.getCity());
        address.setPostalCode(request.getPostalCode());
        address.setStreetLine1(request.getStreetLine1());
        address.setStreetLine2(request.getStreetLine2());
    }
}
