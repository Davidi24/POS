package pos.pos.restaurant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantBrandingResponse {

    private UUID id;
    private String logoUrl;
    private String primaryColor;
    private String secondaryColor;
    private String receiptHeader;
    private String receiptFooter;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
