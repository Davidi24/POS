package pos.pos.restaurant.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertRestaurantBrandingRequest {

    @Size(max = 2048, message = "logoUrl must be at most 2048 characters")
    private String logoUrl;

    @Size(max = 2000, message = "receiptHeader must be at most 2000 characters")
    private String receiptHeader;

    @Size(max = 2000, message = "receiptFooter must be at most 2000 characters")
    private String receiptFooter;
}
