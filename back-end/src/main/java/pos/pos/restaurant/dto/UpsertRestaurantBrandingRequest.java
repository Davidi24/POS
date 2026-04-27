package pos.pos.restaurant.dto;

import jakarta.validation.constraints.Pattern;
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

    @Pattern(
            regexp = "^$|^#(?:[0-9a-fA-F]{3}){1,2}$",
            message = "primaryColor must be a valid hex color"
    )
    @Size(max = 20, message = "primaryColor must be at most 20 characters")
    private String primaryColor;

    @Pattern(
            regexp = "^$|^#(?:[0-9a-fA-F]{3}){1,2}$",
            message = "secondaryColor must be a valid hex color"
    )
    @Size(max = 20, message = "secondaryColor must be at most 20 characters")
    private String secondaryColor;

    @Size(max = 2000, message = "receiptHeader must be at most 2000 characters")
    private String receiptHeader;

    @Size(max = 2000, message = "receiptFooter must be at most 2000 characters")
    private String receiptFooter;
}
