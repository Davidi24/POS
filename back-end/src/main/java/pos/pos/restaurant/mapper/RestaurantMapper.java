package pos.pos.restaurant.mapper;

import org.springframework.stereotype.Component;
import pos.pos.restaurant.dto.RestaurantResponse;
import pos.pos.restaurant.entity.Restaurant;

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
                .createdAt(restaurant.getCreatedAt())
                .updatedAt(restaurant.getUpdatedAt())
                .build();
    }
}
