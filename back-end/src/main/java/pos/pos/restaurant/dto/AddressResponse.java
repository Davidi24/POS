package pos.pos.restaurant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pos.pos.restaurant.enums.AddressType;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressResponse {

    private UUID id;
    private AddressType addressType;
    private String country;
    private String city;
    private String postalCode;
    private String streetLine1;
    private String streetLine2;
    private Boolean isPrimary;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
