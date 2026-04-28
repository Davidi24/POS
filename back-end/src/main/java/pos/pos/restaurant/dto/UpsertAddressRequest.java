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
}
