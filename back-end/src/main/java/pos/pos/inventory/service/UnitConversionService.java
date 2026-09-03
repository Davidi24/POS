package pos.pos.inventory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pos.pos.exception.inventory.InventoryUnitConversionNotFoundException;
import pos.pos.inventory.entity.InventoryItem;
import pos.pos.inventory.entity.InventoryUnitConversion;
import pos.pos.inventory.enums.InventoryUnit;
import pos.pos.inventory.repository.InventoryUnitConversionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

// Converts a quantity from one unit to another for a specific item. Checked in order:
//   1. fromUnit == toUnit -> unchanged.
//   2. Universal fixed ratios (GRAM<->KILOGRAM, OUNCE<->GRAM, etc.) -- true physical constants,
//      hardcoded here rather than stored as data, since they never change and apply to every
//      item, not just one.
//   3. Item-specific rules (InventoryUnitConversion rows) -- for conversions that only make
//      sense for one particular item, like "1 CASE = 12 BOTTLE" for one specific product.
// If none of those resolve it, this refuses to guess: it throws rather than silently applying
// a wrong or made-up ratio. A weight<->count conversion like KILOGRAM to EACH has no universal
// physical constant -- "how many of this item is one kilogram" only makes sense per item -- so
// it can only ever be resolved by tier 3, never invented here.
@Service
@RequiredArgsConstructor
public class UnitConversionService {

    private static final Map<UnitPair, BigDecimal> UNIVERSAL_CONVERSIONS = buildUniversalConversions();

    private final InventoryUnitConversionRepository inventoryUnitConversionRepository;

    @Transactional(readOnly = true)
    public BigDecimal convert(InventoryItem item, BigDecimal quantity, InventoryUnit fromUnit, InventoryUnit toUnit) {
        if (fromUnit == toUnit) {
            return quantity;
        }

        BigDecimal universalFactor = lookupFactor(UNIVERSAL_CONVERSIONS, fromUnit, toUnit);
        if (universalFactor != null) {
            return quantity.multiply(universalFactor);
        }

        BigDecimal itemFactor = itemSpecificFactor(item, fromUnit, toUnit);
        if (itemFactor != null) {
            return quantity.multiply(itemFactor);
        }

        throw new InventoryUnitConversionNotFoundException(item.getName(), fromUnit, toUnit);
    }

    // Checks the item's own rules for this exact direction first; if only the reverse direction
    // was ever recorded (toUnit -> fromUnit), uses that one inverted instead of requiring both
    // directions to be entered separately.
    private BigDecimal itemSpecificFactor(InventoryItem item, InventoryUnit fromUnit, InventoryUnit toUnit) {
        return inventoryUnitConversionRepository
                .findByInventoryItem_IdAndFromUnitAndToUnit(item.getId(), fromUnit, toUnit)
                .map(InventoryUnitConversion::getConversionFactor)
                .or(() -> inventoryUnitConversionRepository
                        .findByInventoryItem_IdAndFromUnitAndToUnit(item.getId(), toUnit, fromUnit)
                        .map(reverse -> invert(reverse.getConversionFactor())))
                .orElse(null);
    }

    private static BigDecimal lookupFactor(Map<UnitPair, BigDecimal> table, InventoryUnit fromUnit, InventoryUnit toUnit) {
        BigDecimal direct = table.get(new UnitPair(fromUnit, toUnit));
        if (direct != null) {
            return direct;
        }

        BigDecimal reverse = table.get(new UnitPair(toUnit, fromUnit));
        return reverse == null ? null : invert(reverse);
    }

    private static BigDecimal invert(BigDecimal factor) {
        return BigDecimal.ONE.divide(factor, 10, RoundingMode.HALF_UP);
    }

    // Each entry means "1 fromUnit = N toUnit", same meaning as
    // InventoryUnitConversion.conversionFactor. Only true, fixed, universally-applicable
    // physical ratios belong here -- anything that depends on the specific item (like how many
    // bottles are in a case) must be a per-item rule instead, never added to this table.
    private static Map<UnitPair, BigDecimal> buildUniversalConversions() {
        Map<UnitPair, BigDecimal> table = new HashMap<>();
        table.put(new UnitPair(InventoryUnit.KILOGRAM, InventoryUnit.GRAM), new BigDecimal("1000"));
        table.put(new UnitPair(InventoryUnit.LITER, InventoryUnit.MILLILITER), new BigDecimal("1000"));
        table.put(new UnitPair(InventoryUnit.TABLESPOON, InventoryUnit.TEASPOON), new BigDecimal("3"));
        table.put(new UnitPair(InventoryUnit.CUP, InventoryUnit.TABLESPOON), new BigDecimal("16"));
        table.put(new UnitPair(InventoryUnit.OUNCE, InventoryUnit.GRAM), new BigDecimal("28.3495"));
        table.put(new UnitPair(InventoryUnit.POUND, InventoryUnit.OUNCE), new BigDecimal("16"));
        table.put(new UnitPair(InventoryUnit.POUND, InventoryUnit.KILOGRAM), new BigDecimal("0.453592"));
        return Map.copyOf(table);
    }

    private record UnitPair(InventoryUnit fromUnit, InventoryUnit toUnit) {
    }
}
