package pos.pos.restaurant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.restaurant.enums.AddressType;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertAddressRequest {

    @NotNull(message = "addressType is required")
    private AddressType addressType;

    @NotBlank(message = "country is required")
    @Size(max = 100, message = "country must be at most 100 characters")
    private String country;

    @NotBlank(message = "city is required")
    @Size(max = 100, message = "city must be at most 100 characters")
    private String city;

    @Size(max = 20, message = "postalCode must be at most 20 characters")
    private String postalCode;

    @NotBlank(message = "streetLine1 is required")
    @Size(max = 255, message = "streetLine1 must be at most 255 characters")
    private String streetLine1;

    @Size(max = 255, message = "streetLine2 must be at most 255 characters")
    private String streetLine2;

    private Boolean isPrimary;

    // A branch's exact spot (both or neither); restaurant addresses don't keep it.
    @jakarta.validation.constraints.DecimalMin(value = "-90", message = "latitude must be between -90 and 90")
    @jakarta.validation.constraints.DecimalMax(value = "90", message = "latitude must be between -90 and 90")
    private java.math.BigDecimal latitude;

    @jakarta.validation.constraints.DecimalMin(value = "-180", message = "longitude must be between -180 and 180")
    @jakarta.validation.constraints.DecimalMax(value = "180", message = "longitude must be between -180 and 180")
    private java.math.BigDecimal longitude;

    @jakarta.validation.constraints.AssertTrue(message = "latitude and longitude go together")
    private boolean isCoordinatesTogether() {
        return (latitude == null) == (longitude == null);
    }
}
