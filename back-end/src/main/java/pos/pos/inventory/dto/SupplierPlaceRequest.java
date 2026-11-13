package pos.pos.inventory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

// One of a supplier's places. A new place may bring its own id.
@Data
public class SupplierPlaceRequest {

    private UUID id;

    @NotBlank(message = "Give the place a name")
    @Size(max = 120, message = "name must be at most 120 characters")
    private String name;

    @Size(max = 240, message = "address must be at most 240 characters")
    private String address;

    @Size(max = 40, message = "phone must be at most 40 characters")
    private String phone;

    @Size(max = 7, message = "deliveryDays can have at most 7 days")
    private Set<@Min(1) @Max(7) Integer> deliveryDays;
}
