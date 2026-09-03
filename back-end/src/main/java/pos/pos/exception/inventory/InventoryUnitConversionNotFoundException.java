package pos.pos.exception.inventory;

import org.springframework.http.HttpStatus;
import pos.pos.exception.auth.AuthException;
import pos.pos.inventory.enums.InventoryUnit;

// Thrown when no conversion path exists between two units for an item -- neither a universal
// physical ratio nor an item-specific rule. This means a missing rule, not a bug, so the message
// names exactly what's missing (the item and both units) rather than a generic error.
public class InventoryUnitConversionNotFoundException extends AuthException {

    public InventoryUnitConversionNotFoundException(String itemName, InventoryUnit fromUnit, InventoryUnit toUnit) {
        super(
                "No conversion exists from " + fromUnit + " to " + toUnit + " for \"" + itemName + "\". "
                        + "Add an item-specific unit conversion rule before using these units together.",
                HttpStatus.BAD_REQUEST
        );
    }
}
