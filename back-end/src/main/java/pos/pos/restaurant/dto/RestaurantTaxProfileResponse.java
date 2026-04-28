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
public class RestaurantTaxProfileResponse {

    private UUID id;
    private String country;
    private String fiscalCode;
    private String taxNumber;
    private String vatNumber;
    private String taxOffice;
    private Boolean isDefault;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
