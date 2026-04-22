package pos.pos.restaurant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.restaurant.enums.RestaurantStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantResponse {

    private UUID id;
    private String name;
    private String legalName;
    private String code;
    private String slug;
    private String description;
    private String email;
    private String phone;
    private String website;
    private String currency;
    private String timezone;
    private Boolean isActive;
    private RestaurantStatus status;
    private UUID ownerUserId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
