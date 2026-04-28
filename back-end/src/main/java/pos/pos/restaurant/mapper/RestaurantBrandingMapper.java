package pos.pos.restaurant.mapper;

import org.springframework.stereotype.Component;
import pos.pos.restaurant.dto.RestaurantBrandingResponse;
import pos.pos.restaurant.dto.UpsertRestaurantBrandingRequest;
import pos.pos.restaurant.entity.Restaurant;
import pos.pos.restaurant.entity.RestaurantBranding;

import java.util.UUID;

@Component
public class RestaurantBrandingMapper {

    public RestaurantBrandingResponse toResponse(RestaurantBranding branding) {
        if (branding == null) {
            return null;
        }

        return RestaurantBrandingResponse.builder()
                .id(branding.getId())
                .logoUrl(branding.getLogoUrl())
                .primaryColor(branding.getPrimaryColor())
                .secondaryColor(branding.getSecondaryColor())
                .receiptHeader(branding.getReceiptHeader())
                .receiptFooter(branding.getReceiptFooter())
                .createdAt(branding.getCreatedAt())
                .updatedAt(branding.getUpdatedAt())
                .build();
    }

    public RestaurantBranding toNewEntity(Restaurant restaurant, UpsertRestaurantBrandingRequest request, UUID actorId) {
        RestaurantBranding branding = new RestaurantBranding();
        branding.setRestaurant(restaurant);
        applyFields(branding, request);
        branding.setCreatedBy(actorId);
        branding.setUpdatedBy(actorId);
        return branding;
    }

    public void updateEntity(RestaurantBranding branding, UpsertRestaurantBrandingRequest request, UUID actorId) {
        applyFields(branding, request);
        branding.setUpdatedBy(actorId);
    }

    private void applyFields(RestaurantBranding branding, UpsertRestaurantBrandingRequest request) {
        branding.setLogoUrl(request.getLogoUrl());
        branding.setPrimaryColor(request.getPrimaryColor());
        branding.setSecondaryColor(request.getSecondaryColor());
        branding.setReceiptHeader(request.getReceiptHeader());
        branding.setReceiptFooter(request.getReceiptFooter());
    }
}
