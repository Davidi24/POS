package pos.pos.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertRestaurantTaxProfileRequest {

    @NotBlank(message = "country is required")
    @Size(max = 100, message = "country must be at most 100 characters")
    private String country;

    @Size(max = 100, message = "fiscalCode must be at most 100 characters")
    private String fiscalCode;

    @Size(max = 100, message = "taxNumber must be at most 100 characters")
    private String taxNumber;

    @Size(max = 100, message = "vatNumber must be at most 100 characters")
    private String vatNumber;

    @Size(max = 150, message = "taxOffice must be at most 150 characters")
    private String taxOffice;

    private Boolean isDefault;

    private OffsetDateTime effectiveFrom;

    private OffsetDateTime effectiveTo;
}
