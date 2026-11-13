package pos.pos.inventory.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

// An item you buy from a supplier: their code and their price per unit of the item (both may be left out).
@Data
public class SupplierItemRequest {

    @Size(max = 100, message = "code must be at most 100 characters")
    private String code;

    @DecimalMin(value = "0", message = "price can't be below 0")
    @DecimalMax(value = "999999999", message = "price is too high")
    private BigDecimal price;
}
