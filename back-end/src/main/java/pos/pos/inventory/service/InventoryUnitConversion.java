package pos.pos.inventory.service;

import pos.pos.inventory.enums.InventoryUnit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Converts only units with an unambiguous physical relationship. Package and piece sizes need item-specific data. */
public final class InventoryUnitConversion {

    private static final int SCALE = 9;
    private static final Map<InventoryUnit, BigDecimal> MASS_IN_GRAMS = Map.of(
            InventoryUnit.GRAM, BigDecimal.ONE,
            InventoryUnit.KILOGRAM, new BigDecimal("1000"),
            InventoryUnit.OUNCE, new BigDecimal("28.349523125"),
            InventoryUnit.POUND, new BigDecimal("453.59237")
    );
    private static final Map<InventoryUnit, BigDecimal> VOLUME_IN_MILLILITERS = Map.of(
            InventoryUnit.MILLILITER, BigDecimal.ONE,
            InventoryUnit.LITER, new BigDecimal("1000")
    );

    private InventoryUnitConversion() {
    }

    public static boolean canConvert(InventoryUnit from, InventoryUnit to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        return sameDimension(MASS_IN_GRAMS, from, to) || sameDimension(VOLUME_IN_MILLILITERS, from, to);
    }

    public static BigDecimal convert(BigDecimal quantity, InventoryUnit from, InventoryUnit to) {
        if (!canConvert(from, to)) {
            throw new IllegalArgumentException("Cannot convert " + from + " to " + to);
        }
        if (from == to) {
            return quantity;
        }

        Map<InventoryUnit, BigDecimal> factors = MASS_IN_GRAMS.containsKey(from) ? MASS_IN_GRAMS : VOLUME_IN_MILLILITERS;
        return quantity.multiply(factors.get(from)).divide(factors.get(to), SCALE, RoundingMode.HALF_UP);
    }

    private static boolean sameDimension(Map<InventoryUnit, BigDecimal> factors, InventoryUnit from, InventoryUnit to) {
        return factors.containsKey(from) && factors.containsKey(to);
    }
}
