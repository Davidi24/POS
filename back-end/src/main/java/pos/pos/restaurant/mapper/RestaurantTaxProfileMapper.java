package pos.pos.restaurant.mapper;

import org.springframework.stereotype.Component;
import pos.pos.restaurant.dto.RestaurantTaxProfileResponse;
import pos.pos.restaurant.dto.UpsertRestaurantTaxProfileRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.entity.RestaurantTaxProfile;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class RestaurantTaxProfileMapper {

    public RestaurantTaxProfileResponse toResponse(RestaurantTaxProfile taxProfile) {
        if (taxProfile == null) {
            return null;
        }

        return RestaurantTaxProfileResponse.builder()
                .id(taxProfile.getId())
                .country(taxProfile.getCountry())
                .fiscalCode(taxProfile.getFiscalCode())
                .taxNumber(taxProfile.getTaxNumber())
                .vatNumber(taxProfile.getVatNumber())
                .taxOffice(taxProfile.getTaxOffice())
                .isDefault(taxProfile.isDefault())
                .effectiveFrom(taxProfile.getEffectiveFrom())
                .effectiveTo(taxProfile.getEffectiveTo())
                .createdAt(taxProfile.getCreatedAt())
                .updatedAt(taxProfile.getUpdatedAt())
                .build();
    }

    public RestaurantTaxProfile toNewEntity(Restaurant restaurant, UpsertRestaurantTaxProfileRequest request, UUID actorId) {
        RestaurantTaxProfile taxProfile = new RestaurantTaxProfile();
        taxProfile.setRestaurant(restaurant);
        applyFields(taxProfile, request);
        taxProfile.setDefault(Boolean.TRUE.equals(request.getIsDefault()));
        taxProfile.setCreatedBy(actorId);
        taxProfile.setUpdatedBy(actorId);
        return taxProfile;
    }

    public void updateEntity(RestaurantTaxProfile taxProfile, UpsertRestaurantTaxProfileRequest request, UUID actorId) {
        applyFields(taxProfile, request);
        taxProfile.setDefault(Boolean.TRUE.equals(request.getIsDefault()));
        taxProfile.setUpdatedBy(actorId);
    }

    public void updateDefault(RestaurantTaxProfile taxProfile, boolean isDefault, UUID actorId) {
        taxProfile.setDefault(isDefault);
        taxProfile.setUpdatedBy(actorId);
    }

    public void markDeleted(RestaurantTaxProfile taxProfile, UUID actorId, OffsetDateTime deletedAt) {
        taxProfile.setDeletedAt(deletedAt);
        taxProfile.setUpdatedBy(actorId);
    }

    private void applyFields(RestaurantTaxProfile taxProfile, UpsertRestaurantTaxProfileRequest request) {
        taxProfile.setCountry(request.getCountry());
        taxProfile.setFiscalCode(request.getFiscalCode());
        taxProfile.setTaxNumber(request.getTaxNumber());
        taxProfile.setVatNumber(request.getVatNumber());
        taxProfile.setTaxOffice(request.getTaxOffice());
        taxProfile.setEffectiveFrom(request.getEffectiveFrom());
        taxProfile.setEffectiveTo(request.getEffectiveTo());
    }
}
