package pos.pos.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

// A supplier's details. A new supplier may bring its own id (the app shows it at once and sends the same id).
@Data
public class SupplierRequest {

    private UUID id;

    @NotBlank(message = "Write the supplier's name")
    @Size(max = 150, message = "name must be at most 150 characters")
    private String name;

    @Size(max = 150, message = "contactName must be at most 150 characters")
    private String contactName;

    @Size(max = 40, message = "phone must be at most 40 characters")
    private String phone;

    @Size(max = 150, message = "email must be at most 150 characters")
    private String email;

    @Size(max = 240, message = "address must be at most 240 characters")
    private String address;

    @Size(max = 2000, message = "notes must be at most 2000 characters")
    private String notes;

    private Boolean active;
}
